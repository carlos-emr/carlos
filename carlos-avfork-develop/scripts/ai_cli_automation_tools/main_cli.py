#!/usr/bin/env python3
"""
Main CLI for GitHub Code Scanning Alert Management
Supports both remediation and false positive detection using AI tools (Aider, Claude Code, etc.)
"""

import sys
import argparse
import json
from typing import Optional
from pathlib import Path
from datetime import datetime

# Import from the github_code_scanning subfolder
from github_code_scanning.github_api import GitHubCodeScanning
from github_code_scanning.ai_automation import AIAutomation


def setup_argparse() -> argparse.ArgumentParser:
    """Setup the command line argument parser"""
    parser = argparse.ArgumentParser(
        description="GitHub code scanning alert management with AI assistance (Aider, Claude Code, etc.)"
    )
    
    parser.add_argument(
        "--mode",
        choices=["fix", "analyze"],
        default="fix",
        help="Mode: 'fix' to remediate issues, 'analyze' to detect false positives"
    )
    
    parser.add_argument(
        "--ai-tool",
        choices=["claude-code", "aider", "custom"],
        default="claude-code",
        help="Which AI tool to use"
    )
    
    parser.add_argument(
        "--owner", 
        default="openo-beta",
        help="GitHub repository owner"
    )
    
    parser.add_argument(
        "--repo",
        default="Open-O",
        help="GitHub repository name"
    )
    
    parser.add_argument(
        "--rule",
        help="Filter by specific rule ID"
    )
    
    parser.add_argument(
        "--severity",
        choices=["critical", "high", "medium", "low"],
        help="Filter by severity level"
    )
    
    parser.add_argument(
        "--state",
        default="open",
        choices=["open", "closed", "dismissed", "fixed"],
        help="Alert state to fetch"
    )

    parser.add_argument(
        "--limit",
        type=int,
        help="Limit number of alerts to process"
    )
    
    parser.add_argument(
        "--output",
        help="Output file name for analysis results (JSON format)"
    )

    parser.add_argument(
        "--verbose-output",
        action="store_true",
        help="Increased detail of output results (JSON format)"
    )

    parser.add_argument(
        "--starting-id",
        type=int,
        help="Starting alert ID (useful for resuming interrupted runs, processes alerts with ID <= starting-id)"
    )
    
    return parser

def initialize_clients(owner, repo, ai_tool):
    """
    Initilize the GitHub and AI clients
        
    Args:
        owner: GitHub repository owner
        repo: GitHub repository name
        ai_tool: The AI tool to use (aider, claude-code, custom)
    Returns:
        GitHubCodeScanning instance, and AIAutomation instance
    """
    
    print(f"Initializing {ai_tool}…")
    return GitHubCodeScanning(owner, repo), AIAutomation(ai_tool=ai_tool)

def load_and_limit_alerts(github_client, state, rule, severity, limit, starting_id):
    """
    Loads the github alerts and applies any limit if specified
        
    Args:
        github_client: The github client instance
        state: The alert state to filter on
        rule: The rule ID to filter on
        severity: The severity level to filter on
        limit: The maximum number of alerts to return (None for no limit)
    Returns:
        List of alert dictionaries
    """

    alerts = github_client.fetch_alerts(
        state=state, rule_filter=rule, severity_filter=severity, starting_id=starting_id
    )
    if limit:
        alerts = alerts[:limit]
    print(f"Found {len(alerts)} alerts to process.")
    return alerts

def print_analysis_summary(results):
    """
    Prints the analysis summary to the console
        
    Args:
        results: The result tally (false positives, security issues, unclear)
    """

    print("\n" + "=" * 60)
    print("Analysis Summary")
    print("=" * 60)
    print(f"False Positives: {len(results.get('false_positives', []))}")
    print(f"Security Issues:  {len(results.get('security_issues', []))}")
    print(f"Unclear/Review:   {len(results.get('unclear', []))}")

def save_results(output, args, results):
    """
    Saves the results to a JSON file in the output directory
        
    Args:
        output: The output file name
        args: The command line arguments
        results: The results dictionary to save to the output file
    """

    output_dir = Path(__file__).parent / "output"
    output_dir.mkdir(exist_ok=True)
    path = output_dir / output
    
    # Build output structure
    output_data = {
        "timestamp": datetime.now().isoformat(),
        "mode": args.mode,
        "ai_tool": args.ai_tool,
        "repository": f"{args.owner}/{args.repo}",
    }
    
    # Process each category in results
    for category, items in results.items():
        if not args.verbose_output and isinstance(items, list):
            # Filter to only essential fields
            filtered_items = [
                {
                    "alert": {
                        "number": item.get("alert", {}).get("number"),
                        "html_url": item.get("alert", {}).get("html_url")
                    },
                    "analysis": item.get("analysis")
                }
                for item in items
            ]
            output_data[category] = filtered_items
        else:
            # Include everything
            output_data[category] = items
    
    with open(path, "w") as f:
        json.dump(output_data, f, indent=2, default=str)
    print(f"\nResults saved to: {path}")

def main():
    """Main function to run the CLI tool"""
    args = setup_argparse().parse_args()
    try:
        github, ai = initialize_clients(args.owner, args.repo, args.ai_tool)
        alerts = load_and_limit_alerts(github, args.state, args.rule, args.severity, args.limit, args.starting_id)
        if not alerts:
            print("No matching alerts found.")
            return

        if args.mode == "fix":
            ai.fix_alerts(alerts)
            results = None
            print("Alerts fully processed for remediation. Please check your unstaged changes in git for the fixes.")
        else:
            results = ai.analyze_alerts(alerts)
            print_analysis_summary(results)

        if args.output and results is not None:
            save_results(args.output, args, results)

    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()