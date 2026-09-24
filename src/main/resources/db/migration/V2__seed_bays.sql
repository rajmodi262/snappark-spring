-- Starting layout: 12 bays across two floors.
insert into parking_slot (slot_number, floor_no, type, status) values
    ('B-01', 0, 'BIKE', 'AVAILABLE'),
    ('B-02', 0, 'BIKE', 'AVAILABLE'),
    ('B-03', 0, 'BIKE', 'AVAILABLE'),
    ('B-04', 0, 'BIKE', 'AVAILABLE'),
    ('C-01', 0, 'CAR',  'AVAILABLE'),
    ('C-02', 0, 'CAR',  'AVAILABLE'),
    ('C-03', 1, 'CAR',  'AVAILABLE'),
    ('C-04', 1, 'CAR',  'AVAILABLE'),
    ('C-05', 1, 'CAR',  'AVAILABLE'),
    ('C-06', 1, 'CAR',  'AVAILABLE'),
    ('S-01', 1, 'SUV',  'AVAILABLE'),
    ('S-02', 1, 'SUV',  'AVAILABLE');
