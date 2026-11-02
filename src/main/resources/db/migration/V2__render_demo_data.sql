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
     'Government order describing eligibility, required documents, and application steps for the education scholarship scheme.'),
    ('DPDF01', 'DEMO ONLY - Person Details: Nandhini', 'Certificate', 3,
     'Synthetic demo profile summary for Nandhini. No real personal information.'),
    ('DPDF02', 'DEMO ONLY - Service Profile: Nandhini', 'Form', 3,
     'Synthetic demo service profile for Nandhini. No real personal information.'),
    ('DPDF03', 'DEMO ONLY - Person Details: Meenakshi', 'Certificate', 3,
     'Synthetic demo profile summary for Meenakshi. No real personal information.'),
    ('DPDF04', 'DEMO ONLY - Service Profile: Meenakshi', 'Form', 3,
     'Synthetic demo service profile for Meenakshi. No real personal information.'),
    ('DPDF05', 'DEMO ONLY - Person Details: Kavitha', 'Certificate', 3,
     'Synthetic demo profile summary for Kavitha. No real personal information.'),
    ('DPDF06', 'DEMO ONLY - Service Profile: Kavitha', 'Form', 3,
     'Synthetic demo service profile for Kavitha. No real personal information.'),
    ('DPDF07', 'DEMO ONLY - Person Details: Divya', 'Certificate', 3,
     'Synthetic demo profile summary for Divya. No real personal information.'),
    ('DPDF08', 'DEMO ONLY - Service Profile: Divya', 'Form', 3,
     'Synthetic demo service profile for Divya. No real personal information.'),
    ('DPDF09', 'DEMO ONLY - Person Details: Ananya', 'Certificate', 3,
     'Synthetic demo profile summary for Ananya. No real personal information.'),
    ('DPDF10', 'DEMO ONLY - Service Profile: Ananya', 'Form', 3,
     'Synthetic demo service profile for Ananya. No real personal information.');

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
UPDATE documents SET citizen_id = 'C003' WHERE document_id IN ('DPDF01', 'DPDF02');
UPDATE documents SET citizen_id = 'C004' WHERE document_id IN ('DPDF03', 'DPDF04');
UPDATE documents SET citizen_id = 'C005' WHERE document_id IN ('DPDF05', 'DPDF06');
UPDATE documents SET citizen_id = 'C006' WHERE document_id IN ('DPDF07', 'DPDF08');
UPDATE documents SET citizen_id = 'C007' WHERE document_id IN ('DPDF09', 'DPDF10');

INSERT INTO document_versions
    (document_id, version_number, text_content, is_current)
SELECT document_id, 1, current_text, TRUE
FROM documents;
