#!/usr/bin/env python3
"""
AI Automation Module
Handles prompt generation and AI CLI interactions (Aider, Claude Code, etc.)
"""

import subprocess
import json
import sys
from typing import Dict, Optional, List
from pathlib import Path
import textwrap

class AIAutomation:
    def __init__(self, ai_script_path: str = "./scripts/ai_cli_automation_tools/setup/run_ai.sh", ai_tool: str = "claude-code"):
        """
        Initialize AI automation
        
        Args:
            ai_script_path: Path to the AI shell script
            ai_tool: Which AI tool to use ('claude-code', 'aider', etc.)
        """
        
        self.ai_script_path = ai_script_path
        self.ai_tool = ai_tool
    
    FIX_PROMPT = textwrap.dedent("""[SECURITY FIX CONTEXT]
                    Fix the specific vulnerability below. Focus on this issue, but consider:
                    - Existing security utilities/libraries used in this codebase
                    - The data flow that leads to this vulnerability
                    - Project coding patterns and conventions where relevant

                    Please fix this security vulnerability:
                    **Issue:** {rule_id} - {description}
                    **Severity:** {severity} | **CWE:** {cwe}
                    **Location:** {path} (lines {start_line}-{end_line})

                    **Requirements:**
                    1. Completely resolve the vulnerability
                    2. Preserve existing functionality
                    3. Follow security best practices
                    4. Use secure coding patterns appropriate for this specific vulnerability. Each fix should be self-contained and not depend on fixes from other alerts.
                    **Context:** {message}""").strip()

    ANALYSIS_PROMPT = textwrap.dedent("""[SECURITY ANALYSIS CONTEXT]
                    Focus on analyzing this specific alert, but you may examine:
                    - The call chain and data flow related to this vulnerability
                    - Input validation and sanitization steps in the execution path
                    - How the flagged variables/methods are defined and used elsewhere
                    - Security controls that may exist upstream or downstream

                    Analyze this alert for false positive:
                    **Alert:** {rule_id} - {description}
                    **Severity:** {severity} | **Location:** {path}:{start_line}-{end_line}
                    **Message:** {message}

                    **Required Analysis:**
                    1. Is this a false positive? (YES/NO)
                    2. Explain reasoning
                    3. Check for: existing mitigations, escaped input, test code, trusted sources

                    **Response Format:** Start with "FALSE POSITIVE:" or "SECURITY ISSUE:" then explain.""").strip()

    def generate_analysis_prompt(self, alert: Dict) -> str:
        """
        Generate a prompt for analyzing if an alert is a false positive
        
        Args:
            alerts: A list of alert dictionaries
            
        Returns:
            String prompt to be build with the template builder
        """
        
        return self.build_prompt(alert, self.ANALYSIS_PROMPT)

    def generate_fix_prompt(self, alert: Dict) -> str:
        """
        Generate a prompt for fixing an alert
        
        Args:
            alerts: A list of alert dictionaries
            
        Returns:
            String prompt to be build with the template builder
        """
        
        return self.build_prompt(alert, self.FIX_PROMPT)

    def build_prompt(self, alert: Dict, template: str) -> str:
        """
        Build a prompt from the alert data and template
        
        Args:
            alert: A single alert passed in
            template: The prompt template to use

        Returns:
            Fully built string prompt for the AI tool
        """

        rule = alert['rule']
        loc = alert['most_recent_instance']['location']
        msg = alert.get('most_recent_instance', {}).get('message', {}).get('text', '')
        
        return template.format(
            rule_id=rule['id'],
            description=rule.get('full_description', rule.get('description', 'N/A')),
            severity=rule.get('security_severity_level', 'N/A'),
            cwe=rule.get('tags', []),
            path=loc['path'],
            start_line=loc.get('start_line', 'N/A'),
            end_line=loc.get('end_line', 'N/A'),
            start_col=loc.get('start_column', 'N/A'),
            end_col=loc.get('end_column', 'N/A'),
            message=msg or 'See the highlighted code section'
        )

    def execute_ai(self, prompt: str, mode: str) -> str:
        """
        Executes the AI tool with the given prompt and mode
        
        Args:
            prompt: The full prompt given to the AI
            mode: 'fix' to remediate issues, 'analyze' to detect false positives
        Returns:
            output string from the AI tool
        """

        cmd = (["claude", "--print", prompt] 
               if self.ai_tool=="claude-code" and mode=="analyze"
               else [self.ai_script_path, self.ai_tool, prompt])
        proc = subprocess.run(cmd, capture_output=True, text=True)
        out = proc.stdout if proc.returncode==0 else proc.stderr
        return out or "[No response]"

    def _categorize(self, text: str) -> str:
        """
        Creates the category of the AI analysis based on the response
        
        Args:
            text: The full response from the AI
        Returns:
            Category of response from the AI: (false positive, security issue, unclear)
        """
        low = text.lower().lstrip('*').strip()
        if "false positive:" in low:
            return "false_positives"
        if "security issue:" in low:
            return "security_issues"
        return "unclear"

    def _report(self, response: str, category: str):
        """
        Creates a summary report of the AI analysis, and prints to the console
        
        Args:
            response: The full response from the AI
            category: The category of response from the AI: (false positive, security issue, unclear)
        """

        msgs = {
            "false_positives": "→ Identified as a FALSE POSITIVE",
            "security_issues": "→ Identified as a SECURITY ISSUE",
            "unclear": "→ UNCLEAR - requires manual review",
        }
        print("AI Response: \n", response)
        print(f"{msgs[category]}")

    def analyze_alerts(self, alerts: List[Dict]):
        """
        Analyze alerts for false positives
        
        Args:
            alerts: A list of alert dictionaries
        Returns:
            Dictionary with a list of results
        """

        results = { "false_positives": [], "security_issues": [], "unclear": [] }
        for alert in alerts:
            print(f"Analyzing alert: {alert['rule']['id']} at file path: {alert['most_recent_instance']['location']['path']}")
            raw = self.execute_ai(self.generate_analysis_prompt(alert), mode="analyze")
            cat = self._categorize(raw)
            results[cat].append({"alert": alert, "analysis": raw})
            self._report(raw, cat)
        return results

    def fix_alerts(self, alerts: List[Dict]):
        """
        Fix security issues in alerts

        Args:
            alerts: A list of alert dictionaries
        """

        for i, alert in enumerate(alerts, 1):
            path = alert['most_recent_instance']['location']['path']
            rule = alert['rule']['id']

            prompt = self.generate_fix_prompt(alert)
            response = self.execute_ai(prompt, mode="fix")
            print(f"Fixing alert: {alert['rule']['id']} at file path: {alert['most_recent_instance']['location']['path']}")
            print ("Full AI response to fix: \n", response)