#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
#
# Deployment check for the DrugRef development image (issue #6343).
#
#   bash scripts/ci/check-drugref-deployment.sh IMAGE
#
# Starts IMAGE with the bind mounts and environment of the drugref service in
# .devcontainer/docker-compose.yml, against a MariaDB that holds the drugref2 schema as
# .devcontainer/db/scripts/populate_db.sh loads it, and requires the DrugRef XML-RPC servlet to
# answer. That pass only counts because the same check must also reject the image started bare
# (how the old startup check ran it) and the deployment once its database is gone.
#
# Why the servlet and not Tomcat: Tomcat answers on its port, with its own 404, whether or not
# the drugref2 context deployed. That context needs the external context descriptor (the address
# valve in the WAR's own META-INF/context.xml does not parse), drugref2.properties, the Hibernate
# dialect and a reachable database whose schema Hibernate validates at startup. Without any of
# them the whole context fails to start, so a GET on the POST-only servlet answers 405 only once
# the context has started and the servlet has initialised.
#
# Needs docker with the compose plugin, jq and curl. Exits 0 only when every check passes.
set -euo pipefail
shopt -s inherit_errexit

readonly SERVLET_PATH=/drugref2/DrugrefService
readonly TOMCAT_PORT=8080
# Bounds for one Tomcat start and for the schema load, generous for a busy hosted runner.
readonly DEPLOY_TIMEOUT=240
readonly DATABASE_TIMEOUT=300
readonly POLL_SECONDS=2
# Results of probe_servlet.
readonly DEPLOYED=0 NOT_DEPLOYED=1 NO_ANSWER=2
readonly USAGE_ERROR=64

REPO_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
readonly REPO_ROOT
readonly COMPOSE_FILE="$REPO_ROOT/.devcontainer/docker-compose.yml"
# The Compose db service is built from this Dockerfile; its base image is the MariaDB release
# the development database runs.
readonly DATABASE_DOCKERFILE="$REPO_ROOT/.devcontainer/db/Dockerfile"
readonly DRUGREF_SCHEMA="$REPO_ROOT/database/mysql/development-drugref.sql"
readonly DRUGREF_SCHEMA_PATCHES="$REPO_ROOT/database/mysql/drugref"
# The MariaDB image runs the SQL files here, in name order, against MARIADB_DATABASE.
readonly INITDB_DIR=/docker-entrypoint-initdb.d
# The Compose service name of the database (the host db_url in drugref2.properties names) and
# the database populate_db.sh creates.
readonly DATABASE_HOST=db
readonly DATABASE_NAME=drugref2
# `docker compose config` writes every literal $ as $$ so that its output stays valid Compose
# input, JSON included. Values the check uses are decoded with this jq function.
readonly JQ_LITERAL='def literal: gsub("\\$\\$"; "$");'

IMAGE=""
NETWORK=""
COMPOSE_JSON=""
PROBE_RESULT=""
CONTAINERS=()
DEPLOYMENT=()

error() {
  local message=$1
  printf '::error::%s\n' "$message" >&2
  return 0
}

log_group() {
  local title=$1
  printf '::group::%s\n' "$title"
  return 0
}

end_group() {
  printf '::endgroup::\n'
  return 0
}

container_running() {
  local container=$1 state
  state=$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null) || state=false
  if [[ $state == true ]]; then
    return 0
  fi
  return 1
}

http_status() {
  local url=$1
  curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 "$url"
}

explain_status() {
  local status=$1
  case $status in
    404) echo "the drugref2 context is not deployed, or it does not map the servlet" ;;
    403) echo "the context's address filter (RemoteCIDRValve) refused the probe" ;;
    500 | 503) echo "the DrugRef servlet failed to initialise" ;;
    *) echo "expected 405 from the POST-only XML-RPC servlet (200 from builds that also serve GET)" ;;
  esac
  return 0
}

# probe_servlet CONTAINER URL TIMEOUT
# Waits for Tomcat in CONTAINER to answer URL and judges the DrugRef servlet by that answer.
# Tomcat opens its HTTP connector only after every context has deployed or failed, so the first
# complete HTTP answer is final. Sets PROBE_RESULT and returns DEPLOYED, NOT_DEPLOYED or NO_ANSWER.
probe_servlet() {
  local container=$1 url=$2 timeout=$3
  local started=$SECONDS status
  local deadline=$((started + timeout))
  PROBE_RESULT=""
  while ((SECONDS < deadline)); do
    if ! container_running "$container"; then
      PROBE_RESULT="container $container stopped before Tomcat answered"
      return "$NO_ANSWER"
    fi
    # curl can report a status from the headers even when the response body fails to transfer.
    status=$(http_status "$url") || status=000
    case $status in
      200 | 405)
        PROBE_RESULT="HTTP $status from $url after $((SECONDS - started))s: the drugref2 context started and the servlet initialised"
        return "$DEPLOYED"
        ;;
      000)
        sleep "$POLL_SECONDS"
        ;;
      *)
        PROBE_RESULT="HTTP $status from $url after $((SECONDS - started))s: $(explain_status "$status")"
        return "$NOT_DEPLOYED"
        ;;
    esac
  done
  PROBE_RESULT="Tomcat in $container did not answer $url within ${timeout}s"
  return "$NO_ANSWER"
}

# probe_container CONTAINER: probes the servlet on the port CONTAINER publishes on loopback.
# The request reaches Tomcat from the container network's gateway, not from inside the
# container, so the context's address filter meets a container-network client as CARLOS is.
probe_container() {
  local container=$1 address status=0
  address=$(docker port "$container" "$TOMCAT_PORT/tcp" 2>/dev/null) || address=""
  address=${address%%$'\n'*}
  if [[ -z $address ]]; then
    PROBE_RESULT="container $container stopped before it published port $TOMCAT_PORT"
    return "$NO_ANSWER"
  fi
  probe_servlet "$container" "http://$address$SERVLET_PATH" "$DEPLOY_TIMEOUT" || status=$?
  return "$status"
}

# Tomcat writes a failed context start (listener, Spring and Hibernate stack traces) only to
# logs/localhost.*.log, not to the console that `docker logs` shows.
localhost_log() {
  local container=$1
  docker exec "$container" sh -c 'cat /usr/local/tomcat/logs/localhost.*.log' 2>/dev/null || true
  return 0
}

show_console_log() {
  local container=$1
  log_group "$container: docker logs (last 200 lines)"
  docker logs --tail 200 "$container" 2>&1 || true
  end_group
  return 0
}

show_diagnostics() {
  local container=$1
  show_console_log "$container"
  log_group "$container: Tomcat localhost log (last 200 lines)"
  localhost_log "$container" | tail -n 200
  end_group
  return 0
}

# Evidence that a rejected start failed for a reason Tomcat reported.
show_severe_lines() {
  local container=$1 console context
  console=$(docker logs "$container" 2>&1 || true)
  context=$(localhost_log "$container")
  log_group "$container: SEVERE lines logged by Tomcat"
  grep -m 20 -F SEVERE <<<"$console"$'\n'"$context" || echo "(none)"
  end_group
  return 0
}

# expect_rejected DESCRIPTION CONTAINER: a self-test. The check must see Tomcat answer without
# the servlet; passing here would mean it cannot tell an undeployed DrugRef from a deployed one.
expect_rejected() {
  local description=$1 container=$2 status=0
  probe_container "$container" || status=$?
  if [[ $status == "$NOT_DEPLOYED" ]]; then
    echo "Self-test passed: the check rejects $description. $PROBE_RESULT"
    show_severe_lines "$container"
    return 0
  fi
  if [[ $status == "$DEPLOYED" ]]; then
    error "Self-test failed: the check accepted $description. $PROBE_RESULT"
  else
    error "Self-test inconclusive for $description: $PROBE_RESULT"
    show_diagnostics "$container"
  fi
  return 1
}

# expect_deployed DESCRIPTION CONTAINER: the DrugRef servlet must answer.
expect_deployed() {
  local description=$1 container=$2 status=0
  probe_container "$container" || status=$?
  if [[ $status == "$DEPLOYED" ]]; then
    echo "PASS: DrugRef deployed with $description. $PROBE_RESULT"
    return 0
  fi
  error "DrugRef did not deploy with $description: $PROBE_RESULT. Check the context descriptor, drugref2.properties, the Hibernate dialect and the drugref2 schema; Tomcat's localhost log below names the cause."
  show_diagnostics "$container"
  return 1
}

# Prints, each terminated by NUL so a value may hold newlines, the docker run options that
# reproduce the runtime configuration of the Compose drugref service: its bind mounts and its
# environment. The check supplies the image, ports, network, start order and readiness check
# itself. Any other setting fails here rather than being dropped, so the check keeps running
# the image the way Compose does.
deployment_args() {
  jq --join-output "$JQ_LITERAL"'
    .services.drugref
    | (to_entries
        | map(select(.value != null
            and (.key | IN("build", "container_name", "depends_on", "env_file", "environment",
                "healthcheck", "image", "networks", "ports", "pull_policy", "restart",
                "volumes") | not)))
        | map(.key)) as $unsupported
    | if ($unsupported | length) > 0 then
        error("the Compose drugref service sets \($unsupported | join(", ")), which this check does not reproduce")
      else
        (((.volumes // [])[]
          | if .type == "bind" then
              "--mount=type=bind,source=\(.source | literal),target=\(.target | literal)"
                + (if .read_only then ",readonly" else "" end)
            else
              error("the Compose drugref service mounts a \(.type) volume at \(.target); this check reproduces bind mounts only")
            end),
        ((.environment // {}) | to_entries[]
          | "--env=\(.key)" + (if .value == null then "" else "=\(.value | tostring | literal)" end)))
        + "\u0000"
      end
  ' <<<"$COMPOSE_JSON" || return 1
  return 0
}

# Reads the deployment_args records into DEPLOYMENT, one docker run option per element.
load_deployment() {
  local records
  records=$(mktemp)
  if ! deployment_args >"$records"; then
    rm -f "$records"
    error "The Compose drugref service cannot be reproduced; see the jq error above"
    return 1
  fi
  mapfile -d '' -t DEPLOYMENT <"$records"
  rm -f "$records"
  return 0
}

# The root password the Compose db service starts with (from its env_file); DrugRef's
# drugref2.properties has to match it.
database_root_password() {
  local password
  password=$(jq --raw-output "$JQ_LITERAL"'
    .services.db.environment | (.MARIADB_ROOT_PASSWORD // .MYSQL_ROOT_PASSWORD // empty) | literal' \
    <<<"$COMPOSE_JSON") || return 1
  if [[ -z $password ]]; then
    error "The Compose db service sets no MARIADB_ROOT_PASSWORD or MYSQL_ROOT_PASSWORD"
    return 1
  fi
  printf '%s\n' "$password"
  return 0
}

database_image() {
  local image
  image=$(awk 'toupper($1) == "FROM" { print $2; exit }' "$DATABASE_DOCKERFILE")
  if [[ -z $image ]]; then
    error "No FROM line in ${DATABASE_DOCKERFILE#"$REPO_ROOT"/}"
    return 1
  fi
  printf '%s\n' "$image"
  return 0
}

# readonly_mount SOURCE TARGET: the --mount value for a read-only bind mount.
readonly_mount() {
  local source=$1 target=$2
  printf 'type=bind,source=%s,target=%s,readonly\n' "$source" "$target"
  return 0
}

start_drugref() {
  local container=$1
  shift
  CONTAINERS+=("$container")
  if ! docker run --detach --name "$container" --network "$NETWORK" \
    --publish "127.0.0.1::$TOMCAT_PORT" "$@" "$IMAGE" >/dev/null; then
    error "Could not start $container from $IMAGE"
    return 1
  fi
  return 0
}

# Stands in for the Compose db service: the same MariaDB base, the same root password and the
# drugref2 schema loaded as populate_db.sh loads it (the dump, then its patches in order).
start_database() {
  local container=$1 image password patch
  local -a schema=()
  schema+=(--mount "$(readonly_mount "$DRUGREF_SCHEMA" "$INITDB_DIR/00-development-drugref.sql")")
  for patch in "$DRUGREF_SCHEMA_PATCHES"/*.sql; do
    if [[ -f $patch ]]; then
      schema+=(--mount "$(readonly_mount "$patch" "$INITDB_DIR/10-${patch##*/}")")
    fi
  done
  image=$(database_image)
  password=$(database_root_password)
  echo "Starting $image with the $DATABASE_NAME schema..."
  CONTAINERS+=("$container")
  if ! MARIADB_ROOT_PASSWORD=$password docker run --detach --name "$container" --network "$NETWORK" \
    --network-alias "$DATABASE_HOST" --env MARIADB_ROOT_PASSWORD \
    --env "MARIADB_DATABASE=$DATABASE_NAME" "${schema[@]}" "$image" >/dev/null; then
    error "Could not start the $DATABASE_NAME database from $image"
    return 1
  fi
  return 0
}

wait_for_database() {
  local container=$1 password tables
  local deadline=$((SECONDS + DATABASE_TIMEOUT))
  password=$(database_root_password)
  while ((SECONDS < deadline)); do
    if ! container_running "$container"; then
      error "The $DATABASE_NAME database stopped while loading its schema"
      show_console_log "$container"
      return 1
    fi
    # Over TCP: the image loads its initdb files through a socket-only temporary server, so an
    # answer over TCP means the schema load has finished.
    if tables=$(MYSQL_PWD=$password docker exec --env MYSQL_PWD "$container" \
      mariadb --protocol=tcp --host=127.0.0.1 --user=root --batch --skip-column-names \
      --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DATABASE_NAME'" \
      2>/dev/null); then
      if [[ $tables =~ ^[0-9]+$ && $tables -gt 0 ]]; then
        echo "The $DATABASE_NAME database is ready ($tables tables)."
        return 0
      fi
      error "The $DATABASE_NAME database holds no tables after its schema load"
      show_console_log "$container"
      return 1
    fi
    sleep "$POLL_SECONDS"
  done
  error "The $DATABASE_NAME database was not ready within ${DATABASE_TIMEOUT}s"
  show_console_log "$container"
  return 1
}

remove_container() {
  local container=$1
  docker rm --force --volumes "$container" >/dev/null 2>&1 || true
  return 0
}

cleanup() {
  local container
  for container in "${CONTAINERS[@]}"; do
    remove_container "$container"
  done
  if [[ -n $NETWORK ]]; then
    docker network rm "$NETWORK" >/dev/null 2>&1 || true
  fi
  return 0
}

require_tools() {
  local tool missing=0
  for tool in docker jq curl; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      error "$tool is required"
      missing=1
    fi
  done
  if ((missing == 0)) && ! docker compose version >/dev/null 2>&1; then
    error "The docker compose plugin is required"
    missing=1
  fi
  return "$missing"
}

main() {
  if (($# != 1)); then
    echo "Usage: $0 IMAGE" >&2
    return "$USAGE_ERROR"
  fi
  IMAGE=$1
  require_tools
  if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    error "Image $IMAGE is not available locally; build it first"
    return 1
  fi

  local prefix="drugref-check-$$" compose_version
  if ! COMPOSE_JSON=$(docker compose --file "$COMPOSE_FILE" config --format json drugref); then
    error "docker compose could not render the drugref service of ${COMPOSE_FILE#"$REPO_ROOT"/}"
    return 1
  fi
  load_deployment
  compose_version=$(docker compose version --short 2>/dev/null) || compose_version=unknown
  echo "Deployment configuration from ${COMPOSE_FILE#"$REPO_ROOT"/} (service drugref, rendered by Docker Compose $compose_version):"
  printf '  %s\n' "${DEPLOYMENT[@]}"

  NETWORK=$prefix
  trap cleanup EXIT
  if ! docker network create "$NETWORK" >/dev/null; then
    error "Could not create the Docker network $NETWORK"
    return 1
  fi
  # The schema loads while the bare image is checked.
  start_database "$prefix-db"

  start_drugref "$prefix-bare"
  expect_rejected "the bare image (no context descriptor, database settings, dialect or database)" "$prefix-bare"
  remove_container "$prefix-bare"

  wait_for_database "$prefix-db"
  start_drugref "$prefix-deployed" "${DEPLOYMENT[@]}"
  expect_deployed "the Compose deployment configuration and the $DATABASE_NAME database" "$prefix-deployed"
  remove_container "$prefix-deployed"

  # Once the database container is gone, its host name no longer resolves on the network.
  remove_container "$prefix-db"
  start_drugref "$prefix-without-database" "${DEPLOYMENT[@]}"
  expect_rejected "the Compose deployment configuration without its database" "$prefix-without-database"

  echo "DrugRef deploys as CARLOS runs it, and the check rejects the image without that configuration."
  return 0
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  main "$@"
fi
