#!/bin/bash

# DESCRIPTION
# This script has been created to automate the upgrade between Hibernate 5.2.18 and 5.6.15.
#
# This script runs aider with Deepseek recursively
# on all files that end with 'daos.java', 'Dao.java',
# 'DAO.java', 'daoImpl.java', 'DaoImpl.java', or 'DAOImpl.java'.
# 
# Aider is prompted to update all JPA HQL queries to use proper
# positional parameter arguments (e.g. ?1, ?2, ?3).
# 
# Ensure that the DEEPSEEK_API_KEY env variable is set to your Deepseek key.
# See https://aider.chat/docs/llms/deepseek.html for more info.

# Use the current working directory as the target directory
target_dir=$(pwd)

# Find all files ending with 'daos.java', 'Dao.java', 'DAO.java', 'daoImpl.java', 'DaoImpl.java', or 'DAOImpl.java' (case insensitive)
find "$target_dir" -type f -iname '*daos.java' -o -iname '*daoimpl.java' | while read -r file; do
    echo "Processing $file..."

    # Run aider in unattended mode with Deepseek, automatically committing changes and resetting chat history
    python -m aider "$file" --yes --deepseek --no-suggest-shell-commands --message "I am upgrading my project to Hibernate 5.6. Please check if there are HQL queries in this file. If there are no queries, do not look for other files, do not make any changes, do not recommend any diff.  If you find HQL, check the HQL query uses positional parameters without numbers.  If the HQL query uses positional parameters without numbers, update them to use the correct positional parameter format (e.g., ?1, ?2). Ensure that the meaning of the query remains unchanged.  Do not add comments.  Do not make changes if not needed." --auto-commits --max-chat-history 0
done
