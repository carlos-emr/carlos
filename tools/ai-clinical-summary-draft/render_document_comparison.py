# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Render a local synthetic comparison report as an escaped, self-contained review page."""
import argparse
from html import escape
import json
from pathlib import Path

import document_summary as document
import openrouter_agent as agent

CSS = """
:root { font: 16px/1.45 system-ui,sans-serif; color:#172c36; background:#f2f5f6 }
body { max-width:1500px; margin:0 auto; padding:28px } h1 { font-size:28px; margin:0 0 8px }
h2 { margin:0 0 8px; font-size:22px } h3 { margin:0 0 12px; font-size:18px }
p { margin:8px 0 } nav { display:flex; flex-wrap:wrap; gap:8px; margin:20px 0 }
a { color:#075b75 } nav a { padding:6px 10px; border:1px solid #bdced4; border-radius:5px }
.case { scroll-margin-top:20px; margin:26px 0 50px } .columns { display:grid; grid-template-columns:1fr 1fr; gap:22px }
.panel { padding:22px; background:white; border:1px solid #d4dfe2; border-radius:8px; min-width:0 }
.meta { color:#50636b; font-size:14px; margin-bottom:16px }.notice { background:#fff2d7; border-left:4px solid #ad7000; padding:12px 16px }
.synopsis { padding:12px 14px; background:#edf6f7; border-left:3px solid #167487; margin-bottom:16px }
.point { padding:10px 0; border-bottom:1px solid #e2e8ea }.text { white-space:pre-wrap; overflow-wrap:anywhere }
table { width:100%; border-collapse:collapse; table-layout:fixed } th,td { text-align:left; vertical-align:top; padding:9px 8px; border-bottom:1px solid #dce5e8 }
th { width:110px; color:#285461; font-size:14px; font-weight:650 } td { font-size:15px }
details { margin-top:6px; font-size:13px; color:#4b626c } summary { cursor:pointer }
blockquote { white-space:pre-wrap; margin:8px 0; padding:8px 10px; border-left:2px solid #bed0d7; background:#f6f8f9; overflow-wrap:anywhere }
.source { margin-top:14px } .failure { color:#9b3529 } .issues { margin:12px 0; padding:12px 16px; background:#fff2d7 }
@media(max-width:850px) {body{padding:16px}.columns{grid-template-columns:1fr}th{width:85px}.panel{padding:15px}}
@media print {.case{break-before:page}nav{display:none}.columns{grid-template-columns:1fr 1fr}body{padding:0;background:white}}
"""


def evidence(point):
    return '<details><summary>Source excerpts</summary>' + ''.join(
        '<blockquote>' + escape(s) + '</blockquote>' for s in point['evidence']) + '</details>'


def panel(row, source):
    if not row or not row['accepted']:
        return '<p class="failure">No draft accepted: ' + escape((row or {}).get('error', 'No result')) + '</p>'
    document.validate_output(row['output'], source)
    points = row['output']['points']
    words = sum(len(p['text'].split()) for p in points)
    html = f'<p class="meta">{words} words · {row["seconds"]:.2f} seconds · {len(row["calls"])} successful model calls</p>'
    if row['mode'] != 'facts':
        return html + ''.join('<div class="point"><div class="text">' + escape(p['text']) + '</div>' + evidence(p) + '</div>' for p in points)
    if points[0]['text'].startswith('Summary: '):
        p, points = points[0], points[1:]
        html += '<div class="synopsis"><div class="text">' + escape(p['text'][9:]) + '</div>' + evidence(p) + '</div>'
    html += '<table aria-label="Clinical facts"><tbody>'
    order = ['Impression', 'Procedure', 'History', 'Medical history', 'Family history', 'Findings',
             'Observations', 'Results', 'Medications', 'Allergies', 'Treatment', 'Plan', 'Follow-up', 'Disposition', 'Context']
    def label(point):
        return point['text'].split(': ', 1)[0]
    points = sorted(points, key=lambda p: order.index(label(p)) if label(p) in order else len(order))
    for point in points:
        category, separator, body = point['text'].partition(': ')
        if not separator:
            category, body = 'Details', point['text']
        html += '<tr><th scope="row">' + escape(category) + '</th><td><div class="text">' + escape(body) + '</div>' + evidence(point) + '</td></tr>'
    return html + '</tbody></table>'


def render(report, reviews=None):
    reviews = reviews or {}
    allowed = {body for _, _, body in agent.committed_notes()[0]}
    cases = report['cases']
    if any(case['text'] not in allowed for case in cases):
        raise ValueError('Comparison preview accepts only complete committed synthetic notes')
    html = '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">'
    html += '<title>Document summary comparison</title><style>' + CSS + '</style><body>'
    html += '<h1>Short overview + facts table</h1><p>Comparison using invented NHS test documents. No real patient data.</p>'
    html += '<p class="notice">Experimental preview. Model checks do not establish clinical completeness. The existing gateway is unchanged. Word counts include the synopsis and fact labels, excluding closed evidence.</p>'
    html += '<nav aria-label="Test documents">' + ''.join('<a href="#' + escape(c['case'], quote=True) + '">' + escape(c['case']) + '</a>' for c in cases) + '</nav>'
    for case in cases:
        rows = {r['mode']: r for r in report['runs'] if r['case'] == case['case']}
        html += '<section class="case" id="' + escape(case['case'], quote=True) + '"><h2>' + escape(case['case']) + '</h2>'
        if case['case'] in reviews:
            html += '<div class="issues"><strong>Manual source comparison</strong><ul>' + ''.join('<li>' + escape(s) + '</li>' for s in reviews[case['case']]) + '</ul></div>'
        html += '<div class="columns">'
        for mode, title in [('balanced', 'Current summary'), ('facts', 'Overview + facts trial')]:
            html += '<article class="panel ' + mode + '"><h3>' + title + '</h3>' + panel(rows.get(mode), case['text']) + '</article>'
        html += '</div><details class="source"><summary>Full synthetic source · ' + str(len(case['text'].split())) + ' words</summary><blockquote>' + escape(case['text']) + '</blockquote></details></section>'
    return html + '</body></html>'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--reviews', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text())
    reviews = json.loads(args.reviews.read_text()) if args.reviews else None
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(report, reviews))
    print(args.output)


if __name__ == '__main__':
    main()
