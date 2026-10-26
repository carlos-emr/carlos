# eChart document grouping

In **Preferences → Display & UI → eChart Document Grouping**, choose:

- **No grouping (newest first)**: one flat document list, ordered by observation date descending. Documents without a usable date appear last. This is the default, including when no preference has been saved.
- **Folders by document type**: collapsed folders with document counts. Open a folder to see its documents, newest first. Documents without a type share **Other documents**.

Save with **Save All**, then reload the chart. The choice belongs to the provider viewing the chart, independently of the appointment provider. Existing document permissions, omitted document types, and document links still apply.

The release uses the same provider property as the document folders on `develop`: `echart_show_group_document_by_type`. Only the stored value `true` (surrounding whitespace ignored) enables grouping; missing, blank, `false`, and unrecognized values show the flat list. No database migration is required.
