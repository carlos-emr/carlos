#!/usr/bin/env python3
"""
Generate BCrypt password hashes compatible with OpenO EMR
=========================================================

This script generates BCrypt password hashes in the format expected by OpenO's
DelegatingPasswordEncoder: {bcrypt}$2b$12$...

Usage:
    python3 generate_bcrypt_password.py <password>
    python3 generate_bcrypt_password.py "my_password"

Examples:
    python3 generate_bcrypt_password.py openo2025
    python3 generate_bcrypt_password.py "MySecurePassword123"

The generated hash can be directly inserted into the OpenO database's
security.password field.

Requirements:
    - Python 3.x
    - bcrypt library (pip3 install bcrypt)
"""

import sys
import bcrypt
import getpass


def generate_bcrypt_hash(password, rounds=12):
    """
    Generate a BCrypt hash in Spring Security DelegatingPasswordEncoder format.
    
    Args:
        password (str): The password to hash
        rounds (int): BCrypt rounds (cost factor), default 12
        
    Returns:
        str: BCrypt hash in format {bcrypt}$2b$12$...
    """
    # Generate salt and hash
    salt = bcrypt.gensalt(rounds=rounds)
    hashed = bcrypt.hashpw(password.encode('utf-8'), salt)
    
    # Format for Spring Security DelegatingPasswordEncoder
    spring_format = '{bcrypt}' + hashed.decode('utf-8')
    
    return spring_format


def verify_hash(password, hash_string):
    """
    Verify a password against a BCrypt hash.
    
    Args:
        password (str): The plain text password
        hash_string (str): The BCrypt hash (with or without {bcrypt} prefix)
        
    Returns:
        bool: True if password matches hash
    """
    # Remove the {bcrypt} prefix if present
    if hash_string.startswith('{bcrypt}'):
        hash_string = hash_string[8:]
    
    return bcrypt.checkpw(password.encode('utf-8'), hash_string.encode('utf-8'))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        print("\nInteractive mode:")
        password = getpass.getpass("Enter password to hash: ")
        if not password.strip():
            print("Error: Empty password")
            sys.exit(1)
    else:
        password = sys.argv[1]
    
    # Generate hash
    hash_result = generate_bcrypt_hash(password)
    
    # Display results
    print(f"Password: {password}")
    print(f"BCrypt hash: {hash_result}")
    print()
    print("Database SQL:")
    print(f"UPDATE security SET password='{hash_result}' WHERE user_name='your_username';")
    print()
    
    # Verify the hash works
    if verify_hash(password, hash_result):
        print("✓ Hash verification successful")
    else:
        print("✗ Hash verification failed")
    
    print()
    print("Notes:")
    print("- This hash uses BCrypt with cost factor 12 (recommended)")
    print("- The {bcrypt} prefix is required for OpenO's DelegatingPasswordEncoder")
    print("- Store the full hash including the {bcrypt} prefix in the database")


if __name__ == '__main__':
    main()