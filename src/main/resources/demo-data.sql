INSERT INTO documents (document_id, title, category, importance, current_text)
VALUES
    ('D101', 'Property Tax Circular', 'Circular', 5,
     'Updated property tax instructions for assessment, online payment, and receipt retrieval.'),
    ('D102', 'Birth Certificate Application', 'Certificate', 4,
     'Application form and document verification guidance for birth certificates.'),
    ('D103', 'Property Tax Payment Notice', 'Notice', 3,
     'Notice for property tax payment deadlines, late fees, and payment options.'),
    ('D104', 'Public Building Permit Guidelines', 'Permit', 4,
     'Guidance on building permit applications, required plans, inspections, and approval status.'),
    ('D105', 'Scholarship Scheme Order', 'Order', 5,
     'Government order describing eligibility, required documents, and application steps for the education scholarship scheme.');

INSERT INTO citizens (citizen_id, full_name)
VALUES
    ('C001', 'Arun Kumar'),
    ('C002', 'Priya Sharma'),
    ('C003', 'Nandhini'),
    ('C004', 'Meenakshi'),
    ('C005', 'Kavitha'),
    ('C006', 'Divya'),
    ('C007', 'Ananya');

UPDATE documents SET citizen_id = 'C001' WHERE document_id = 'D102';
UPDATE documents SET citizen_id = 'C002' WHERE document_id = 'D105';

INSERT INTO document_versions
    (document_id, version_number, text_content, is_current)
SELECT document_id, 1, current_text, TRUE
FROM documents;
