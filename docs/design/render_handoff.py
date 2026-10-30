"""Render the generated Word summary using the official packaged renderer.

Run with the workspace's bundled Python. Pass --renderer to render_docx.py;
--font-dir may be repeated to resolve CJK fonts in a headless environment.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

parser=argparse.ArgumentParser()
parser.add_argument('--renderer',required=True,type=Path)
parser.add_argument('--font-dir',action='append',default=[],type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parents[2]
output=root/'.cache/design-handoff/render-final'
output.mkdir(parents=True,exist_ok=True)
env=os.environ.copy()
if args.font_dir:
    config=ET.Element('fontconfig')
    for font_dir in args.font_dir:
        if not font_dir.is_dir():raise SystemExit(f'Font directory missing: {font_dir}')
        ET.SubElement(config,'dir').text=str(font_dir.resolve())
    font_cache=output.parent/'fontconfig-cache';font_cache.mkdir(exist_ok=True)
    ET.SubElement(config,'cachedir').text=str(font_cache)
    path=output.parent/'render-fonts.conf'
    ET.ElementTree(config).write(path,encoding='utf-8',xml_declaration=True)
    env['FONTCONFIG_FILE']=str(path)
subprocess.run([sys.executable,str(args.renderer.resolve()),str(root/'docs/design/B_Design_and_Integration_Handoff.docx'),
                '--output_dir',str(output),'--emit_pdf'],check=True,env=env)
