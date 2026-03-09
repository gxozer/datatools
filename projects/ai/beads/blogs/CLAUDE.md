# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Environment

- **Python**: 3.14.3 (Homebrew: `/opt/homebrew/opt/python@3.14/bin`)
- **Virtual environment**: `.venv/` (activate with `source .venv/bin/activate`)
- **Formatter**: Black

## Commands

```bash
# Set up environment
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt  # once requirements.txt exists

# Run
python main.py

# Freeze dependencies
pip freeze > requirements.txt
```

## Project Status

Early-stage project. `main.py` is currently PyCharm boilerplate. Real implementation is pending.
