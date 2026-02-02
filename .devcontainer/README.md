# Open-OSP EMR DEVCONTAINER Environment Setup

This document outlines the steps for setting up your development environment for the Open-OSP EMR project using Docker
and VS-Code.

## Prerequisites

* **Docker Desktop:** Installed and running
* **VS Code:** Installed with the "Dev Containers" extension
    * Install "Dev Containers" Extension: Open VS Code, click on the Extensions icon (four squares in the left sidebar),
      search for "Dev Containers" by Microsoft and click "Install".
* **Git:** Installed

## Optional: If you are developing on windows it's recommended that you work within WSL (Windows Subsystem for Linux)

### Steps to get started within WSL 

1. **Go to the command line and type wsl**
    ```bash 
    wsl
    ```
2. **Navigate to the home directory** 
    ```bash
    cd $HOME
    ```
3. **Create a directory**
    ```bash
    mkdir softsec
    ```
4. **Navigate to the softsec dir**
    ```bash
    cd softsec
    ```

5. **Continue normally from here**

## Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/openo-beta/Open-O.git
   cd open-osp
   ```

2. **Confirm port availability:**
   Before proceeding, ensure that no other processes (Tomcat, MySQL) are using ports 8080 and 3306 on your PC/Macbook.
   Also, ensure that no other docker containers are running on those ports.

3. **Open the project in VS Code:**
    * Open VS Code and navigate to the project directory.
    ```bash
    code ./
    ```
    * VS Code should automatically detect the `.devcontainer` folder and prompt you to "Reopen in Container".
    * Click "Reopen in Container" to start the development environment.
    * *Note - In case "Reopen in Container" option does not work then:*
        * Look on bottom left of VSCode you will find a remote connection icon (green colored). Click on it.
        * It will prompt few option, select "Reopen in Container".

4. Once it Reopen in Container, you need to wait until it finishes the setup process. This process will:
    * Build the Docker images for the Open-OSP application and database.
    * Configure the images and start the containers.
    * Wait for the database to be fully initialized and healthy before starting the application.
      You will see the extensions in the left sidebar along with the "Workspace" folder.
      You will also see the Java extension starts processing the project.

   **Note:** First-time setup may take several minutes as the database initializes from SQL files. The application container waits for the database health check to pass before starting.

### Initial Compilation

Let's now compile Oscar.

This might take absolutely forever when doing it for the first time.

Once you've built it for the first time, a subsequent full build should take about 2 minutes on MacOS and about 8
minutes on Windows, as we'll have managed to cache a bunch of Maven compilation artifacts.

   ```zsh
   make clean
   make install
   ```

Once the compilation is successful, a `target/oscar` directory full of artifacts will be created. This is a so-called "
Exploded WAR".

## Access the application

* Open your web browser and navigate to `http://localhost:8080`.
* You should see the Open-OSP EMR application running.
* Login credentials for local development are: 
    * Username: openodoc
    * Password: openo2025
    * PIN     : 2025
    * **Note**: On first login, you will be forced to change the password. Use the same credentials above to complete the password reset process.

### Subsequent Compilations

For developers who are not compiling for the first time in the dev container, it is recommended to clean the previous build artifacts before compiling again. This ensures a fresh build environment.

   ```zsh
   make clean
   make install
   ```

### Additional Notes About Compilation

Hot Reload Functionality:

- A feature that allows you to save changes to JSP/HTML/CSS files, and see the changes after refreshing the web application page
  - This will automatically be set up on compilation of the project with `make install`. And will only need to be set up if it is not already running in the background
  - On other types of files with new changes that aren't listed with this functionality, you will need to fully rebuild the project with `make clean` and `make install`


### Running Tests

The make script supports various test execution options:

   ```zsh
   # Run all tests (modern JUnit 5 + legacy JUnit 4)
   make install --run-tests

   # Run only modern tests (JUnit 5)
   make install --run-modern-tests

   # Run only legacy tests (JUnit 4)
   make install --run-legacy-tests

   # Run only modern unit tests (fast, mocked dependencies)
   make install --run-unit-tests

   # Run only modern integration tests (slower, real database)
   make install --run-integration-tests
   ```

**Note:** Running tests adds time to the build process. Modern unit tests execute in < 4 seconds, while legacy tests may take 5-15 minutes.

### View OpenO application logs

OpenO logs are sent to the console and can be viewed using Docker commands. The development environment is configured with DEBUG level logging by default for comprehensive troubleshooting.

**View real-time logs:**
   ```bash
   docker logs -f <container_name>
   ```

**Find your container:**
   ```bash
   docker ps
   ```

**Log Configuration:**
- **Default Level**: INFO (configured in `local.env`)
- **Output**: Console only (viewable via `docker logs`)
- **Coverage**: Application flow, authentication, errors, and important events
- **DEBUG Level**: Available but commented out (very verbose, useful for deep troubleshooting)  
- **SQL Logging**: Available but commented out by default (can be enabled in `local.env`)
- **Hot Reload Logging**: Console only (viewable via `/tmp/webapp-watcher.log`)

## Manual Container Management (Without VS Code)

### **Start containers manually:**
```bash
cd .devcontainer
docker-compose up -d
```

### **View container status:**
```bash
docker ps
# or use predictable names:
docker logs -f openo-tomcat-dev
docker logs -f openo-mariadb-dev
```

### **Complete cleanup and reset:**
```bash
# Stop and remove containers with volumes (fresh database + Maven cache)
docker-compose down -v

# Remove all unused Docker resources
docker system prune -f

# Remove specific volumes if they persist
docker volume rm open-o_mariadb-files open-o_m2-volume
```

**Note:** Complete cleanup removes both database data AND Maven cache, requiring full dependency re-download on next build (~15-30 minutes).

### **Quick container restart:**
```bash
# Restart without rebuilding
docker-compose restart

# Rebuild and restart
docker-compose up --build -d
```

### **Maven (.m2) cache management:**
```bash
# View cache size
docker exec openo-tomcat-dev du -sh /root/.m2

# Clear Maven cache (forces fresh download of all dependencies)
docker volume rm open-o_m2-volume

# Or clear cache while container is running
docker exec openo-tomcat-dev rm -rf /root/.m2/repository
```

**When to clear Maven cache:**
- Build failures with dependency conflicts
- After major dependency updates
- Corrupted or incomplete downloads
- Switching between significantly different branches

### **Database troubleshooting:**
```bash
# Check database users
docker exec openo-mariadb-dev mysql -u root -ppassword oscar -e "SELECT user_name, pin FROM security;"

# Reset database only (keeps app container and Maven cache)
docker-compose stop db
docker volume rm open-o_mariadb-files
docker-compose up db -d
```

### **Database Volume Management:**
Database volumes persist data between container restarts. This means that even after rebuilding containers, your database may contain old data unless the volume is explicitly removed.

```bash
# Check database state
docker exec openo-mariadb-dev mysql -u root -ppassword oscar -e "SHOW TABLES;" | wc -l

# Force complete database rebuild from SQL files
docker-compose stop db
docker volume rm open-o_mariadb-files
docker-compose up db -d

# Wait for initialization, then verify clean state
docker logs openo-mariadb-dev | grep "ready for connections"
```

**When to remove database volume:**
- After changing database initialization scripts
- When switching branches with different database schemas
- To ensure a completely clean development environment
- When database appears corrupted or inconsistent

**Important:** Volume removal destroys ALL database data. This forces recreation from the SQL initialization files but loses any test data you may have added during development.

### **Recent Database Configuration Updates:**
Several important fixes have been applied to ensure stable database initialization:

**Character Set Configuration:**
- MariaDB server now defaults to UTF-8 encoding to match application expectations
- Fixes foreign key constraint errors that occurred with charset mismatches
- Configuration in `.devcontainer/development/config/shared/my.cnf`

**Hibernate Schema Management:**
- Changed from `update` to `validate` mode to prevent automatic schema modifications
- Database schema is now managed entirely through SQL initialization files
- Configuration in `src/main/resources/spring_hibernate.xml`

**Application Startup Dependencies:**
- Added health checks to ensure database is fully ready before application starts
- Prevents connection failures during fresh database initialization
- Application waits up to 10 minutes for database to become healthy

### **Database Health Checks:**
The database container includes health checks to ensure it's fully ready before the application starts:

```bash
# Check database health status
docker ps --format "table {{.Names}}\t{{.Status}}"

# View health check logs
docker inspect openo-mariadb-dev --format='{{json .State.Health}}' | jq

# Force health check manually
docker exec openo-mariadb-dev mysqladmin ping -h localhost -u root -ppassword
```

The application container will wait up to 10 minutes (60s start period + 10 retries × 10s interval) for the database to become healthy. This prevents connection failures during fresh database initialization.

### **Manual Password Reset:**
If you forget your password or need to reset it manually, you can use the BCrypt password generation script:

```bash
# Generate a new BCrypt hash for your password
python3 scripts/generate_bcrypt_password.py

# Enter your desired password when prompted
# Copy the generated hash (starts with {bcrypt}$2b$...)

# Update the database with the new hash
docker exec openo-mariadb-dev mysql -u root -ppassword oscar -e \
  "UPDATE security SET password='YOUR_BCRYPT_HASH_HERE' WHERE user_name='openodoc';"

# Optional: Force password reset on next login
docker exec openo-mariadb-dev mysql -u root -ppassword oscar -e \
  "UPDATE security SET forcePasswordReset=1 WHERE user_name='openodoc';"

# Verify the change
docker exec openo-mariadb-dev mysql -u root -ppassword oscar -e \
  "SELECT user_name, LEFT(password, 20) as password_start FROM security WHERE user_name='openodoc';"
```

**Example:**
```bash
# Generate hash
$ python3 scripts/generate_bcrypt_password.py
Enter password: mynewpassword
Generated BCrypt hash: {bcrypt}$2b$12$abc123...xyz789

# Update database
$ docker exec openo-mariadb-dev mysql -u root -ppassword oscar -e \
  "UPDATE security SET password='{bcrypt}\$2b\$12\$abc123...xyz789' WHERE user_name='openodoc';"
```

**Important Notes:**
- Escape the `$` characters in the hash when using it in shell commands (use `\$`)
- The hash format must include the `{bcrypt}` prefix
- See `docs/Password_System.md` for detailed information about password management

## Logging Configuration

The development environment uses configurable logging levels in `local.env`:

### **Available Log Levels:**
- **ERROR**: Only errors and critical issues
- **WARN**: Warnings and errors  
- **INFO**: Application flow, authentication, important events *(default)*
- **DEBUG**: Very verbose internal details, method calls, parameter values

### **Common Logging Scenarios:**
```bash
# General development (default)
LOG_VERBOSITY=INFO

# Deep troubleshooting (authentication issues, complex bugs)
LOG_VERBOSITY=DEBUG

# Production-like (minimal noise)  
LOG_VERBOSITY=WARN
```

### **Enable SQL Query Logging:**
Uncomment in `local.env` for database debugging:
```bash
HIBERNATE_SHOW_SQL=true
HIBERNATE_FORMAT_SQL=true
```

**Note:** Changes require container restart: `docker-compose restart`

## Additional Notes

* The `.devcontainer/development/config/shared/local.env` file contains environment variables that can be customized for your development environment.
* You can find more information about the OpenO EMR project and its development environment in the project's documentation.

## Switching Branches 

* If you're having issues with the containers after changing branches, it may be due to the container configuration being different
  on the new branch from the one you were on. 
* Fix: 
    1. Close Visual Studio Code.
    2. Remove the containers, images, and volumes from docker so that the new containers can be loaded.
    3. Relaunch Visual Studio Code. This will allow the new containers to be built. 
  
## Build Issues 

* Ensure prior to a build with changes or switching branches you run

    ```zsh 
    make clean
    ```
* If the issue still persists remove the .m2 cache and then run the make clean command. 
  
## Files Included in the Dev-Container Environment

* **`.devcontainer/devcontainer.json`:** Defines the configuration for the development environment, including Docker
  images to use, ports to forward, and user settings.
* **`.devcontainer/development/Dockerfile`:** Defines the Docker image for the Open-OSP application.
* **`.devcontainer/db/Dockerfile`:** Defines the Docker image for the Open-OSP database.
* **`.devcontainer/development/setup/setup.sh`:** Automates the setup process for the development environment.
* **` .devcontainer/development/setup/seed_data.sh`:** Automates the setup process for the needed test data files, that will be added into the test database when building the container.
* **` .devcontainer/development/setup/setup-hot-reload.sh`:** Automates the setup process for the hot reload functionality in the development environment when building the project.
* **`.devcontainer/docker-compose.yml`:** Defines the Docker Compose configuration for the development environment,
  including the services to run and their dependencies.

## Playwright Configuration

The devcontainer includes Playwright for browser automation and UI testing. The Playwright MCP server configuration must have the correct Chromium executable path that matches the installed Playwright version.

### Verifying Playwright Setup

To verify the Playwright Chromium path is correctly configured:

```bash
# Inside the devcontainer
/workspace/.devcontainer/scripts/verify-playwright-path.sh
```

This script checks that:
- Playwright is installed at the expected version
- Chromium browser is installed in the correct location
- The `.mcp.json` configuration has the matching executable path

**See [PLAYWRIGHT_SETUP.md](PLAYWRIGHT_SETUP.md) for detailed documentation** on Playwright configuration, version management, and troubleshooting.

### Quick Reference

- **Playwright Version**: Defined in `.devcontainer/development/Dockerfile`
- **Configuration File**: `.mcp.json` in repository root
- **Verification Script**: `.devcontainer/scripts/verify-playwright-path.sh`

## Additional Resources

* **Docker Desktop:** https://www.docker.com/products/docker-desktop
* **Dev Containers Extension:** https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers
* **VS Code Developing with Dev Containers:** https://code.visualstudio.com/docs/devcontainers/containers
* **Docker Desktop Guide: Getting Started:** https://docs.docker.com/desktop
* **Docker Compose:** https://docs.docker.com/compose**
* **Dockerfile:** https://docs.docker.com/reference/dockerfile/

## Opening the Dev-Container Environment in VS Code through WSL

* **Note:** Make sure you have the Prerequisites installed and configured. 

### Steps

1. **Go to the command line and type wsl**
    ```bash 
    wsl
    ```
2. **Navigate to the home directory** 
    ```bash
    cd $HOME
    ```
3. **Create a directory**
    ```bash
    mkdir softsec
    ```
4. **Navigate to the softsec dir**
    ```bash
    cd softsec
    ```
5. **Clone the GitHub repo Open-O**
    ```bash
    git clone https://github.com/cc-ar-emr/Open-O.git
    ```
6. **Navigate to the Open-O repo dir**
    ```bash
    cd Open-O
    ```
7. **Start VS Code.**
    ```bash
    code ./
    ```

* This should open the Open-O repo in VS Code and prompt you to reopen the folder in the dev container environment.

### Checksum locks

Whenever you update, add, or remove a library ensure that the changes are reflected in the dependencies-lock.json. 

When updating a library, the integrity value needs to be updated to correspond with the change. 

Keeping these locks in sync ensures reproducible builds and guards against tampered artifacts.
Only update when you trust the signature of the libraries. 
mvn se.vandmo:dependency-lock-maven-plugin:lock

### Debugging in Tomcat. Setup steps and usage

Download Community Server Connectors in extensions.
 Ensure its set to version - v0.26.19

Add new server. 
    use on disk - /usr/local/tomact/

add make2 file in .devcontainer/development/scripts/make2 

paste in.

#!/usr/bin/env sh

command=$1
skip_tests=true

if [ "$command" = "test" ]; then
  skip_tests=false
fi

case $command in
  clean)
    mvn clean
    ;;
  lock)
    mvn se.vandmo:dependency-lock-maven-plugin:lock
    ;;
  *)
    mvn clean -Dmaven.test.skip=$skip_tests prepare-package war:exploded

    # Assuming the pom.xml file is in the current directory
    pom_file="pom.xml"

    # This version number is the deployable war version from here: https://bitbucket.org/MagentaHealth/oscar/src/master/pom.xml
    # <artifactId>oscar</artifactId>
	  # <packaging>war</packaging>
	  # <version>v24.02.0</version>
	  # <name>OpenOSCAR</name>
    # Extract the first occurrence of version from pom.xml
    version=$(grep -oPm 1 '<version>\K[^<]+' "$pom_file")

    # It *should* be possible to deploy that `oscar-0.0.0-SNAPSHOT` directory to Tomcat using the `oscar-0.0.0-SNAPSHOT` name,
    # but unknown bugs prevent the app from starting up if that name is used. :man_shrugging:
    # So, let's rename it to `oscar`. Then, our properties file in the home directory can also be called `oscar.properties`
    # (it's already been symlinked by the setup scripts).
    # We'll also create a symlink so that we can continue to compile into `oscar-0.0.0-SNAPSHOT`,
    # which is where Maven expects to deposit its artifacts.
    snapshot_src_dir='oscar-'$version
    snapshot_dest_dir='oscar'

    # Only make the link if it hasn't already been linked yet
    if [ -d "target/$snapshot_src_dir" ] && [ ! -L "target/$snapshot_src_dir" ]; then
      mv target/$snapshot_src_dir target/$snapshot_dest_dir
      ln -s $snapshot_dest_dir target/$snapshot_src_dir
    fi
    ;;
esac

run the following commands 

chmod +x ./devcontainer/development/scripts/make2 

run the script. 

./devcontainer/development/scripts/make2

if you encounter an error 
"/usr/bin/env: ‘sh\r’: No such file or directory
/usr/bin/env: use -[v]S to pass options in shebang lines" 

run: 
apt-get update && apt-get install -y dos2unix
dos2unix .devcontainer/development/scripts/make2

then re-run the script. 

right click the tomcat server 

"add deployment" 

Click: exploded

Path: /workspace/target/oscar

Click: select exploded deployment

Right click tomcat server

Publish Server 

Run Debug Mode

You should now be able to use breakpoints within vscode! 

## Enjoy developing with Open-OSP!
