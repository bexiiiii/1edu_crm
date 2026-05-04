import csv
import json
import re
import sys
import uuid
from datetime import date
from pathlib import Path

BASE = Path('/Applications/development/paraplan/export_20260501_125457')
SCHEMA = 'tenant_3cba4fa05506473797a28a66fd63c177'
NOW_SQL = 'CURRENT_TIMESTAMP'

BRANCHES = {
    'Aksuabad':       '59de8cca-1537-43db-b71f-9f65f43f756d',
    'Kids-_ЕНТ':      '0a78d6f3-2946-4951-9038-73cca87a9768',
    'Zaytuna_Academy': '9f3b229e-0840-4769-b5a0-069fd65eab49',
    'Main-_главный':  '472ab9db-e78b-4806-bf99-27df61d96990',
}

SUFFIX = '20260501_125457'

# Pre-scan: find employee IDs that appear in multiple branches → use NULL branch_id
def _collect_emp_branch_counts():
    from collections import Counter
    all_ids = []
    for folder in BRANCHES:
        emp_file = BASE / folder / f'employees_{SUFFIX}.json'
        if emp_file.exists():
            with emp_file.open(encoding='utf-8') as fh:
                for emp in json.load(fh):
                    all_ids.append(emp['id'])
    counts = Counter(all_ids)
    return {eid for eid, cnt in counts.items() if cnt > 1}

MULTI_BRANCH_EMPLOYEES = _collect_emp_branch_counts()


def sql_str(value):
    if value is None:
        return 'NULL'
    return "'" + str(value).replace("'", "''") + "'"


def sql_date(value):
    if value is None or value == '':
        return 'NULL'
    if isinstance(value, date):
        return f"DATE '{value.isoformat()}'"
    return f"DATE '{value}'"


def sql_json(value):
    return sql_str(json.dumps(value, ensure_ascii=False)) + '::jsonb'


def normalize_phone(value):
    if not value:
        return None
    cleaned = re.sub(r'\s+', '', str(value).strip())
    return cleaned or None


def split_name(full_name):
    # Paraplan format: "Фамилия Имя Отчество"
    # CRM DB: first_name=Имя (parts[1]), last_name=Фамилия (parts[0]), middle_name=Отчество (parts[2+])
    parts = [p for p in re.split(r'\s+', (full_name or '').strip()) if p]
    if not parts:
        return None, None, None
    if len(parts) == 1:
        return parts[0], parts[0], None
    if len(parts) == 2:
        return parts[1], parts[0], None
    return parts[1], parts[0], ' '.join(parts[2:])


def parse_birthday(value):
    if not value:
        return None
    if isinstance(value, dict):
        try:
            return date(int(value['year']), int(value['month']), int(value['day']))
        except (KeyError, ValueError):
            return None
    if isinstance(value, str):
        value = value.strip()
        if not value:
            return None
        try:
            return date.fromisoformat(value)
        except ValueError:
            return None
    return None


def emit_insert(table, columns, rows, conflict_target=None, update_columns=None):
    if not rows:
        return
    print(f'INSERT INTO {table} ({", ".join(columns)}) VALUES')
    print(',\n'.join(rows))
    if conflict_target and update_columns:
        assignments = ', '.join(f'{c} = EXCLUDED.{c}' for c in update_columns)
        print(f'ON CONFLICT {conflict_target} DO UPDATE SET {assignments};')
    elif conflict_target:
        print(f'ON CONFLICT {conflict_target} DO NOTHING;')
    else:
        print(';')
    print()


print('BEGIN;')
print()

total_students = 0
total_staff = 0

for folder, branch_id in BRANCHES.items():
    students_file = BASE / folder / f'students_{SUFFIX}.json'
    employees_file = BASE / folder / f'employees_{SUFFIX}.json'

    # --- STUDENTS ---
    with students_file.open(encoding='utf-8') as fh:
        students = json.load(fh)

    student_rows = []
    for student in students:
        first_name, last_name, middle_name = split_name(student.get('name'))
        if not first_name or not last_name:
            print(f'-- SKIP student bad name: {student.get("name")!r}', file=sys.stderr)
            continue
        primary_phone = normalize_phone((student.get('phone') or {}).get('phone'))
        phone_list = [normalize_phone(item.get('phone')) for item in student.get('phoneList', []) if item.get('phone')]
        contact_list = [item.get('contactId') for item in student.get('contactList', []) if item.get('contactId')]
        additional_phones = [p for p in phone_list[1:] if p]
        parent_phone = additional_phones[0] if additional_phones else None
        school = student.get('school') or {}
        balance = student.get('balance')
        metadata = {
            'source_id': student['id'],
            'source_status_id': student.get('statusId'),
            'balance': balance,
            'phone_list': phone_list,
            'contact_list': contact_list,
            'additional_phones': additional_phones,
            'school_name': school.get('name'),
            'school_grade': school.get('grade'),
        }
        student_rows.append(
            '(' + ', '.join([
                sql_str(student['id']),
                sql_str(first_name),
                sql_str(last_name),
                sql_str(middle_name),
                sql_str(student.get('name')),
                'NULL',
                sql_str(student.get('email')),
                sql_str(primary_phone),
                sql_date(parse_birthday(student.get('birthday'))),
                sql_str('ACTIVE'),
                sql_str(None),
                sql_str(parent_phone),
                sql_str(primary_phone),
                sql_str(student.get('sex')),
                sql_str(student.get('address')),
                sql_str(None),
                sql_str(student.get('comment')),
                sql_str(school.get('name')),
                sql_str(school.get('grade')),
                sql_str(None),
                sql_str(None),
                sql_str(None),
                sql_str(student.get('comment')),
                sql_json(metadata),
                NOW_SQL,
                NOW_SQL,
                '0',
                sql_str(branch_id),
                '0',
            ]) + ')'
        )

    print(f'-- {folder}: inserting {len(student_rows)} students')
    emit_insert(
        f'{SCHEMA}.students',
        [
            'id', 'first_name', 'last_name', 'middle_name', 'customer', 'student_photo', 'email', 'phone',
            'birth_date', 'status', 'parent_name', 'parent_phone', 'student_phone', 'gender', 'address', 'city',
            'notes', 'school', 'grade', 'additional_info', 'contract', 'discount', 'comment', 'metadata',
            'created_at', 'updated_at', 'version', 'branch_id', 'discount_percent',
        ],
        student_rows,
        '(id)',
        [
            'first_name', 'last_name', 'middle_name', 'customer', 'email', 'phone', 'birth_date', 'status',
            'parent_name', 'parent_phone', 'student_phone', 'gender', 'address', 'city', 'notes', 'school',
            'grade', 'additional_info', 'contract', 'discount', 'comment', 'metadata', 'updated_at',
            'version', 'branch_id', 'discount_percent',
        ],
    )
    total_students += len(student_rows)

    # --- EMPLOYEES / STAFF ---
    with employees_file.open(encoding='utf-8') as fh:
        employees = json.load(fh)

    # Also read CSV for phone/birthday fields that may be richer
    emp_csv_file = BASE / folder / f'employees_{SUFFIX}.csv'
    emp_csv_by_id = {}
    if emp_csv_file.exists():
        with emp_csv_file.open(newline='', encoding='utf-8-sig') as fh:
            for row in csv.DictReader(fh):
                emp_csv_by_id[row['id']] = row

    staff_rows = []
    for emp in employees:
        emp_id = emp['id']
        csv_row = emp_csv_by_id.get(emp_id, {})

        first_name, last_name, middle_name = split_name(emp.get('name'))
        if not first_name or not last_name:
            print(f'-- SKIP employee bad name: {emp.get("name")!r}', file=sys.stderr)
            continue

        email = emp.get('email')
        phone = normalize_phone(
            (emp.get('phone') or {}).get('phone')
            or csv_row.get('phone1')
        )
        gender = emp.get('sex') or csv_row.get('sex') or None
        address = emp.get('address') or csv_row.get('address') or None
        comments = emp.get('comment') or csv_row.get('comment') or None

        raw_bday = emp.get('birthday') or csv_row.get('birthday') or None
        birthdate = parse_birthday(raw_bday)

        raw_status = (emp.get('status') or csv_row.get('status') or '').upper()
        status = 'ACTIVE' if (not raw_status or raw_status == 'CURRENT') else 'INACTIVE'

        # Employees who appear in multiple branches get NULL branch_id (shared across all)
        eff_branch_id = None if emp_id in MULTI_BRANCH_EMPLOYEES else branch_id

        staff_rows.append(
            '(' + ', '.join([
                sql_str(emp_id),
                sql_str(first_name),
                sql_str(last_name),
                sql_str(middle_name),
                sql_str(email),
                sql_str(address),
                sql_str(phone),
                'NULL',
                sql_date(birthdate),
                sql_str(gender),
                sql_str(phone),
                sql_str(comments),
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                sql_str('TEACHER'),
                sql_str(status),
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                'NULL',
                NOW_SQL,
                NOW_SQL,
                sql_str('paraplan-import'),
                'NULL',
                '0',
                sql_str('FIXED'),
                'NULL',
                'NULL',
                sql_str(eff_branch_id),
            ]) + ')'
        )

    print(f'-- {folder}: inserting {len(staff_rows)} staff')
    emit_insert(
        f'{SCHEMA}.staff',
        [
            'id', 'first_name', 'last_name', 'middle_name', 'email', 'address', 'phone', 'image', 'birthdate',
            'gender', 'phone_number', 'comments', 'document_type', 'document_number', 'document_given_date',
            'issued_by', 'document_file', 'role', 'status', 'position', 'iin', 'order_number', 'contract',
            'contract_date', 'probation_period', 'probation_period_comments', 'hire_date', 'end_hire_date',
            'salary', 'notes', 'created_at', 'updated_at', 'created_by', 'updated_by', 'version',
            'salary_type', 'salary_percentage', 'custom_status', 'branch_id',
        ],
        staff_rows,
        '(id)',
        [
            'first_name', 'last_name', 'middle_name', 'email', 'address', 'phone', 'birthdate', 'gender',
            'phone_number', 'comments', 'role', 'status', 'updated_at', 'version', 'branch_id',
        ],
    )
    total_staff += len(staff_rows)

print('COMMIT;')
print(f'-- TOTAL: students={total_students} staff={total_staff}', file=sys.stderr)
