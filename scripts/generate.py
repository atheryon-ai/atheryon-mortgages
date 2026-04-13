#!/usr/bin/env python3
"""
Atheryon Mortgages - File Generator CLI
Usage:
  python scripts/generate.py srs          # Generate/overwrite SRS
  python scripts/generate.py openapi      # Generate OpenAPI spec
  python scripts/generate.py schemas      # Generate JSON schemas folder
  python scripts/generate.py architecture # Generate architecture doc
"""

import typer
from pathlib import Path
from typing import Optional

app = typer.Typer(help="Atheryon Mortgages File Generator CLI")

def write_file(path: str, content: str, overwrite: bool = True):
    file_path = Path(path)
    file_path.parent.mkdir(parents=True, exist_ok=True)
    
    if file_path.exists() and not overwrite:
        typer.echo(f"⚠️  File already exists: {path}")
        return
    
    file_path.write_text(content.strip() + "\n", encoding="utf-8")
    typer.echo(f"✅ Created/Updated: {path}")

# ====================== TEMPLATES ======================

SRS_TEMPLATE = """# Atheryon Mortgages – Mortgage Origination SRS
**Version**: 1.4
**Date**: {date}
**Repository**: https://github.com/atheryon-ai/atheryon-mortgages

[Full SRS content would go here - I'll expand this later]
"""

# You can add more templates below as we build them

@app.command()
def srs(
    version: str = typer.Option("1.4", help="SRS version"),
    date: str = typer.Option("13 April 2026", help="Date")
):
    """Generate the main Software Requirements Specification"""
    content = SRS_TEMPLATE.format(date=date)
    # TODO: We'll replace this with the full expanded SRS later
    write_file("docs/requirements/mortgage-origination-srs.md", content)

@app.command()
def openapi():
    """Generate OpenAPI/Swagger specification for core services"""
    typer.echo("🚧 OpenAPI generator coming in next update...")
    # We'll fill this in soon

@app.command()
def schemas():
    """Generate detailed JSON Schema files for all entities"""
    typer.echo("🚧 JSON Schemas generator coming soon...")

@app.command()
def architecture():
    """Generate high-level architecture document"""
    typer.echo("🚧 Architecture document coming soon...")

@app.command()
def list():
    """List available generators"""
    typer.echo("Available commands:")
    typer.echo("  srs          → Mortgage Origination SRS")
    typer.echo("  openapi      → OpenAPI specification")
    typer.echo("  schemas      → JSON Schema files")
    typer.echo("  architecture → Architecture document")

if __name__ == "__main__":
    app()
