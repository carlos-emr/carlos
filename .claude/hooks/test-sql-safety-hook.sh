#!/usr/bin/env bash
#
# Test script for the SQL safety hook.
# Sends simulated Edit/Write JSON payloads and verifies exit codes.
#
# Usage: bash .claude/hooks/test-sql-safety-hook.sh
#
# Exit codes from hook:
#   0 = allowed (safe or not applicable)
#   2 = blocked (unsafe SQL detected)
#

set -euo pipefail

HOOK="$(cd "$(dirname "$0")" && pwd)/validate-sql-safety.py"
PASS=0
FAIL=0
TOTAL=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

run_test() {
    local description="$1"
    local expected_exit="$2"
    local json_payload="$3"
    TOTAL=$((TOTAL + 1))

    # Run the hook, capture exit code and stderr output for diagnostics
    set +e
    local hook_output
    hook_output=$(echo "$json_payload" | python3 "$HOOK" 2>&1)
    actual_exit=$?
    set -e

    if [ "$actual_exit" -eq "$expected_exit" ]; then
        echo -e "  ${GREEN}PASS${NC}: $description (exit=$actual_exit)"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC}: $description (expected=$expected_exit, got=$actual_exit)"
        if [ -n "$hook_output" ]; then
            echo -e "    Hook output:"
            echo "$hook_output" | sed 's/^/      /'
        fi
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "============================================"
echo " SQL Safety Hook Test Suite"
echo "============================================"
echo ""

# ----------------------------------------------------------------
# Section 1: FALSE POSITIVES THAT SHOULD NOW PASS (exit 0)
# ----------------------------------------------------------------
echo -e "${YELLOW}--- False Positives (should be ALLOWED, exit 0) ---${NC}"

# Test FP-1: TicklerDaoImpl positional parameter building
run_test "FP-1: Positional param building (TicklerDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/TicklerDaoImpl.java",
    "new_string": "query = query + \" and t.serviceDate >= ?\" + paramIndex++;\n            paramList.add(filter.getStartDate());\n            query.setParameter(paramIndex, value);"
  }
}'

# Test FP-2: TicklerDaoImpl IN clause building
run_test "FP-2: IN clause param building (TicklerDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/TicklerDaoImpl.java",
    "new_string": "query = query + \" and t.creator IN (\";\nfor (int x = 0; x < providers.length; x++) {\n    if (x > 0) { query += \",\"; }\n    query += \"?\" + paramIndex++;\n    paramList.add(providers[x].getProviderNo());\n}\nquery += \")\";\nquery.setParameter(1, val);"
  }
}'

# Test FP-3: TicklerDaoImpl status clause
run_test "FP-3: Status clause with param (TicklerDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/TicklerDaoImpl.java",
    "new_string": "query = query + \" and t.status = ?\" + paramIndex++;\nparamList.add(convertStatus(filter.getStatus()));\nquery.setParameter(paramIndex, value);"
  }
}'

# Test FP-4: BillingDaoImpl named parameter building
run_test "FP-4: Named param building (BillingDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/BillingDaoImpl.java",
    "new_string": "serviceCodeValues.append(\"bd.serviceCode = :\").append(param);\nparams.put(param, serviceCodes.get(i));\nquery.setParameter(e.getKey(), e.getValue());"
  }
}'

# Test FP-5: BillingDaoImpl query fragment concatenation
run_test "FP-5: Query fragment concat (BillingDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/BillingDaoImpl.java",
    "new_string": "String queryString = \"FROM Billing b WHERE b.status like :status \" + providerQuery + startDateQuery + endDateQuery + demoQuery;\nparams.put(\"status\", statusType);\nquery.setParameter(param.getKey(), param.getValue());"
  }
}'

# Test FP-6: BillingDaoImpl conditional clauses with named params
run_test "FP-6: Conditional named param clauses (BillingDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/BillingDaoImpl.java",
    "new_string": "if (providerNo != null) {\n    providerQuery = \" and b.apptProviderNo = :providerNo\";\n    params.put(\"providerNo\", providerNo);\n}\nif (startDate != null) {\n    startDateQuery = \" and ( b.billingDate >= :startDate ) \";\n    params.put(\"startDate\", startDate);\n}\nquery.setParameter(key, value);"
  }
}'

# Test FP-7: EFormDaoImpl entity name from getSimpleName()
run_test "FP-7: Entity name from getSimpleName (EFormDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/EFormDaoImpl.java",
    "new_string": "StringBuilder buf = new StringBuilder(\"FROM \" + modelClass.getSimpleName() + \" ef WHERE ef.current = ?1\");\nQuery query = entityManager.createQuery(buf.toString());\nquery.setParameter(1, status);"
  }
}'

# Test FP-8: StringBuilder with SELECT and append with positional params
run_test "FP-8: StringBuilder SELECT with positional params" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/SomeDaoImpl.java",
    "new_string": "StringBuilder sqlCommand = new StringBuilder(\"select h from \").append(BillingONCHeader1.class.getSimpleName()).append(\" h WHERE \");\nsqlCommand.append(\"h.providerNo = ?\").append(counter++).append(\" AND h.status IN (?\").append(counter++).append(\") \");\nquery.setParameter(counter, val);"
  }
}'

# Test FP-9: Query variable assignment with WHERE clause
run_test "FP-9: Query var assignment with WHERE" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/SomeDaoImpl.java",
    "new_string": "query = selectQuery + \" FROM Tickler t, Demographic d where d.DemographicNo = t.demographicNo and d.ProviderNo = ?\" + paramIndex++;\nparamList.add(filter.getMrp());\nquery.setParameter(1, val);"
  }
}'

# Test FP-10: Comment lines with SQL keywords should be ignored
run_test "FP-10: SQL in comments (should be ignored)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/SomeDaoImpl.java",
    "new_string": "// query = \"SELECT * FROM users WHERE id = \" + userId;\n// This old pattern used string concatenation\nquery.setParameter(1, val);"
  }
}'

# Test FP-11: DxDaoImpl keyword search with positional params
run_test "FP-11: Keyword LIKE search with params (DxDaoImpl)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/DxDaoImpl.java",
    "new_string": "conditions.add(\"(\" + codingSystem + \" like ?\" + paramIndex + \" or description like ?\" + (paramIndex + 1) + \")\");\nquery.setParameter(paramIndex++, likePattern);\nquery.setParameter(paramIndex++, likePattern);"
  }
}'

# Test FP-12: Pure string literal concatenation (splitting long line)
run_test "FP-12: String literal split across lines" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/SomeDaoImpl.java",
    "new_string": "String hql = \"SELECT t FROM Tickler t \" + \"WHERE t.status = :status\";\nquery.setParameter(\"status\", status);"
  }
}'

# Test FP-13: StringBuilder findAll pattern
run_test "FP-13: StringBuilder findAll with optional WHERE" 0 '{
  "tool_name": "Write",
  "tool_input": {
    "file_path": "/path/to/SomeDaoImpl.java",
    "content": "StringBuilder sb = new StringBuilder();\nsb.append(\"select x from \");\nsb.append(modelClass.getSimpleName());\nsb.append(\" x\");\nif (current != null) {\n    sb.append(\" where x.current=?1\");\n}\nQuery query = entityManager.createQuery(sb.toString());\nif (current != null) {\n    query.setParameter(1, current);\n}"
  }
}'

echo ""

# ----------------------------------------------------------------
# Section 2: TRUE POSITIVES THAT MUST STILL BE CAUGHT (exit 2)
# ----------------------------------------------------------------
echo -e "${YELLOW}--- True Positives (should be BLOCKED, exit 2) ---${NC}"

# Test TP-1: Direct value injection
run_test "TP-1: Direct value injection (userId)" 2 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/UnsafeDao.java",
    "new_string": "String sql = \"SELECT * FROM users WHERE id = \" + userId;\nstatement.executeQuery(sql);"
  }
}'

# Test TP-2: String.format with SQL
run_test "TP-2: String.format SQL injection" 2 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/UnsafeDao.java",
    "new_string": "String query = String.format(\"SELECT * FROM users WHERE name = %s\", userName);"
  }
}'

# Test TP-3: Quoted value injection (quote-sandwich pattern)
# Note: Single quotes in JSON content require special bash escaping
run_test "TP-3: Quoted value injection" 2 '{"tool_name":"Edit","tool_input":{"file_path":"/path/to/UnsafeDao.java","new_string":"String sql = \"SELECT * FROM patients WHERE name = '"'"'\" + patientName + \"'"'"'\";" }}'

# Test TP-4: executeQuery with concatenation (no params)
run_test "TP-4: executeQuery with direct concat" 2 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/UnsafeDao.java",
    "new_string": "rs = statement.executeQuery(\"SELECT * FROM users WHERE id = \" + userId);"
  }
}'

# Test TP-5: createQuery with user input concatenation
run_test "TP-5: createQuery with user value concat" 2 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/UnsafeDao.java",
    "new_string": "Query q = entityManager.createQuery(\"SELECT u FROM User u WHERE u.name = \" + userName);"
  }
}'

# Test TP-6: WHERE clause with direct value
run_test "TP-6: WHERE clause direct value injection" 2 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/UnsafeDao.java",
    "new_string": "String condition = \"WHERE status = \" + userStatus;\nString sql = \"SELECT * FROM orders \" + condition;"
  }
}'

# Test TP-7: Mixed safe setParameter and unsafe concatenation in same file
# A single setParameter elsewhere in the file must NOT whitelist an unsafe concat.
# This tests the critical case: file-wide bypass must NOT fire.
run_test "TP-7: Mixed safe setParameter and unsafe concat should be blocked" 2 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/MixedDao.java",
    "new_string": "Query q = entityManager.createQuery(\"SELECT u FROM User u WHERE u.id = :id\");\nq.setParameter(\"id\", userId);\nString sql = \"SELECT * FROM users WHERE name = '"'"'\" + userName + \"'"'"'\";"
  }
}'

echo ""

# ----------------------------------------------------------------
# Section 3: EDGE CASES (various expected outcomes)
# ----------------------------------------------------------------
echo -e "${YELLOW}--- Edge Cases ---${NC}"

# Test EC-1: Non-Java file should always pass
run_test "EC-1: Non-Java file (XML) passes" 0 '{
  "tool_name": "Write",
  "tool_input": {
    "file_path": "/path/to/config.xml",
    "content": "SELECT * FROM users WHERE id = "
  }
}'

# Test EC-2: Read tool should always pass (hook only checks Edit/Write)
run_test "EC-2: Read tool passes regardless" 0 '{
  "tool_name": "Read",
  "tool_input": {
    "file_path": "/path/to/UnsafeDao.java"
  }
}'

# Test EC-3: Empty content should pass
run_test "EC-3: Empty content passes" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/SomeDaoImpl.java",
    "new_string": ""
  }
}'

# Test EC-4: Safe static query (no concatenation at all)
run_test "EC-4: Static parameterized query" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/SafeDao.java",
    "new_string": "Query q = entityManager.createQuery(\"SELECT u FROM User u WHERE u.id = :id\");\nq.setParameter(\"id\", userId);"
  }
}'

# Test EC-5: Criteria API usage (safe)
run_test "EC-5: Criteria API (always safe)" 0 '{
  "tool_name": "Edit",
  "tool_input": {
    "file_path": "/path/to/SafeDao.java",
    "new_string": "CriteriaBuilder cb = entityManager.getCriteriaBuilder();\nCriteriaQuery<User> cq = cb.createQuery(User.class);\nRoot<User> root = cq.from(User.class);\ncq.select(root).where(cb.equal(root.get(\"id\"), userId));"
  }
}'

echo ""

# ----------------------------------------------------------------
# Summary
# ----------------------------------------------------------------
echo "============================================"
echo " RESULTS: $PASS passed, $FAIL failed (out of $TOTAL)"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
