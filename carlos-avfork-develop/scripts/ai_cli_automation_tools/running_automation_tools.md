## Steps to running the automation tools

1. Rebuild your docker container in VSCode:
    - Open Command Palette: `Ctrl+Shift+P` (Windows/Linux) or `Cmd+Shift+P` (macOS)
    - Run: 
    `Dev Containers: Rebuild Container`

2. Create a `.env` file in the `setup` folder and add the following variable:  
    ```env
    PERSONAL_ACCESS_TOKEN=your_token_here
    ```
    *(Must be a classic GitHub token with full repo permissions enabled)*

3. To avoid errors and unnecessary checks with `.min.js` files, create or update an `.aiderignore` file (if you are using aider) at the project root and add:  
    ```
    *.min.js
    ```

4. Input the following command in your terminal use ``Ctrl + Shift + ` `` to open the terminal in VS Code) if you are using API keys:  
    ```bash
    export ANTHROPIC_API_KEY=<your_api_key_here>
    ```
5. Make the required shell script executable in your terminal

    ```bash
        chmod +x scripts/ai_cli_automation_tools/setup/install_packages.sh
        chmod +x scripts/ai_cli_automation_tools/setup/run_ai.sh
    ```

6. Run the install_packages shell script:

     ```bash
        bash ./scripts/ai_cli_automation_tools/setup/install_packages.sh
    ```

    **Before proceeding, if you are using claude code, log in to your account if needed**

7. Run the Python automation script by entering the command:  
    ```bash
    .venv/bin/python3 scripts/ai_cli_automation_tools/main_cli.py
    ```

    **Please type --help at the end of the script call to see all parameters supported**

    **Try to limit file edits per branch to around 20 files if possible**
    - aider will automatically commit changes
    - claude-code will save changes and you will need to commit them yourself

**Possible additions that could be added later:**
- Automated branch and pull request creation    
    - not currently added to give more control for ensuring current alerts are resolved or analyzed without issues