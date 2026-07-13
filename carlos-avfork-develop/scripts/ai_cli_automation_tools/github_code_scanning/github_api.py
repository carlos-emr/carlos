#!/usr/bin/env python3
"""
GitHub Code Scanning API Module
Handles all interactions with GitHub's code scanning alerts API
"""

import os
import requests
from typing import List, Dict, Optional
from dotenv import load_dotenv
from pathlib import Path

class GitHubCodeScanning:  
    def __init__(self, owner: str, repo: str, token: Optional[str] = None):
        """
        Initialize GitHub Code Scanning client
        
        Args:
            owner: GitHub repository owner
            repo: GitHub repository name
            token: GitHub personal access token (optional, will load from env if not provided)
        """
        
        self.owner = owner
        self.repo = repo
        self.api_url = f"https://api.github.com/repos/{owner}/{repo}/code-scanning/alerts"
        self.per_page = 100
        self.token = token or self.load_github_token()
        self.headers = {"Accept": "application/vnd.github+json", "Authorization": f"Bearer {self.token}"}
    
    def get_env_path(self, default_filename=".env"):
        """
        Gets the path to the .env file for the GitHub token

        Args:
            default_filename: Default filename for the .env file
        Returns:
            Full path to the .env file
        """

        script_dir = os.path.dirname(os.path.abspath(__file__))
        return os.path.join(script_dir, "../setup", default_filename)

    def load_github_token(self, dotenv_path=None):
        """
        Load GitHub personal access token from environment
        
        Returns:
            GitHub personal access token
            
        Raises:
            ValueError: If token is not found in environment
        """

        if dotenv_path is None:
            dotenv_path = self.get_env_path()
        load_dotenv(dotenv_path=dotenv_path)
        if token := os.getenv("PERSONAL_ACCESS_TOKEN"):
            return token
        else:
            raise ValueError("PERSONAL_ACCESS_TOKEN was not found in the environment variables")
          
    def fetch_alerts(self, state: str = "open", rule_filter: Optional[str] = None, severity_filter: Optional[str] = None, starting_id: Optional[int] = None) -> List[Dict]:
        """
        Fetch all code scanning alerts with optional filters
        
        Args:
            state: Alert state (open, closed, dismissed, fixed)
            rule_filter: Filter by specific rule ID
            severity_filter: Filter by severity level (critical, high, medium, low)
            
        Returns:
            List of alert dictionaries
        """

        alerts = []
        page = 1
        
        while True:
            params = {
                "per_page": self.per_page,
                "page": page,
                "state": state
            }
            
            response = requests.get(self.api_url, headers=self.headers, params=params, timeout=30)
            
            if response.status_code != 200:
                print(f"Error {response.status_code}: {response.text}")
                break
                
            page_data = response.json()
            if not page_data:
                break
                
            alerts.extend(page_data)
            page += 1
        
        # Apply filters
        if rule_filter:
            alerts = [a for a in alerts if a.get("rule", {}).get("id") == rule_filter]
            
        if severity_filter:
            alerts = [
                a for a in alerts 
                if a.get("rule", {}).get("security_severity_level") == severity_filter
            ]

        if starting_id is not None:
            alerts = [a for a in alerts if a.get("number", 0) <= starting_id]
            
        return alerts