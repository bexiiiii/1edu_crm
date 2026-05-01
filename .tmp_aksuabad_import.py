import csv
import json
import re
import sys
import uuid
from collections import defaultdict
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

BASE = Path('/Users/behruztohtamishov/Downloads/export_20260501_115913/Aksuabad')
BRANCH_ID = '59de8cca-1537-43db-b71f-9f65f43f756d'
CREATED_BY = 'paraplan-import'
NOW_SQL = 'CURRENT_TIMESTAMP'
SCHEMA = 'tenant_3cba4fa05506473797a28a66fd63c177'


def sql_str(value):
    if value is None:
        return 'NULL'
    return "'" + str(value).replace("'", "''") + "'"


def sql_num(value):
    if value is None or value == '':
        return 'NULL'
    if isinstance(value, Decimal):
        return format(value, 'f')
    return format(Decimal(str(value)), 'f')


def sql_date(value):
    if value is None or value == '':
        return 'NULL'
    if isinstance(value, date):
        return f"DATE '{value.isoformat()}'"
    return f"DATE '{value}'"


def sql_ts_from_date(value):
    if value is None or value == '':
        return NOW_SQL
    if isinstance(value, date):
        return f"TIMESTAMPTZ '{value.isoformat()} 00:00:00+00'"
    return f"TIMESTAMPTZ '{value} 00:00:00+00'"


def sql_json(value):
    return sql_str(json.dumps(value, ensure_ascii=False)) + '::jsonb'


def normalize_phone(value):
    if not value:
        return None
    cleaned = re.sub(r'\s+', '', str(value).strip())
    return cleaned or None


def split_name(full_name):
    parts = [part for part in re.split(r'\s+', (full_name or '').strip()) if part]
    if not parts:
        return None, None, None
    if len(parts) == 1:
        return parts[0], parts[0], None
    if len(parts) == 2:
        return parts[0], parts[1], None
    return parts[0], parts[-1], ' '.join(parts[1:-1])


def parse_birthday(value):
    if not value:
        return None
    if isinstance(value, dict):
        return date(int(value['year']), int(value['month']), int(value['day']))
    if isinstance(value, str):
        value = value.strip()
        if not value:
            return None
        if value.startswith('{'):
            bits = dict(re.findall(r"'(year|month|day)'\s*:\s*(\d+)", value))
            if {'year', 'month', 'day'} <= bits.keys():
                return date(int(bits['year']), int(bits['month']), int(bits['day']))
            return None
        return date.fromisoformat(value)
    return None


def earliest(values):
    return min(values) if values else None


def emit_insert(table, columns, rows, conflict_target=None, update_columns=None):
    print(f'INSERT INTO {table} ({", ".join(columns)}) VALUES')
    print(',\n'.join(rows))
    if conflict_target and update_columns:
        assignments = ', '.join(f'{column} = EXCLUDED.{column}' for column in update_columns)
        print(f'ON CONFLICT {conflict_target} DO UPDATE SET {assignments};')
    elif conflict_target:
        print(f'ON CONFLICT {conflict_target} DO NOTHING;')
    else:
        print(';')
    print()


with (BASE / 'students_20260501_115913.json').open(encoding='utf-8') as handle:
    students = json.load(handle)
with (BASE / 'groups_20260501_115913.json').open(encoding='utf-8') as handle:
    groups = json.load(handle)
with (BASE / 'subscriptions_20260501_115913.csv').open(newline='', encoding='utf-8-sig') as handle:
    subscriptions = list(csv.DictReader(handle))

subscription_dates_by_student = defaultdict(list)
subscription_dates_by_group = defaultdict(list)
subscription_dates_by_pair = defaultdict(list)
subscription_amounts_by_group = defaultdict(list)
for row in subscriptions:
    start_date = date.fromisoformat(row['start_date']) if row['start_date'] else None
    purchase_date = date.fromisoformat(row['purchase_date']) if row['purchase_date'] else start_date
    amount = Decimal(row['total_price']) if row['total_price'] else Decimal('0')
    if purchase_date:
        subscription_dates_by_student[row['student_id']].append(purchase_date)
        subscription_dates_by_group[row['group_id']].append(purchase_date)
        subscription_dates_by_pair[(row['group_id'], row['student_id'])].append(purchase_date)
    subscription_amounts_by_group[row['group_id']].append(amount)

student_ids = {student['id'] for student in students}
missing_members = []
for group in groups:
    for member in group.get('studentList', []):
        member_id = member.get('student', {}).get('id')
        if member_id and member_id not in student_ids:
            missing_members.append((group['id'], member_id))

if missing_members:
    raise SystemExit(f'Missing {len(missing_members)} group members in students export: {missing_members[:5]}')

print('BEGIN;')

student_rows = []
for student in students:
    first_name, last_name, middle_name = split_name(student.get('name'))
    if not first_name or not last_name:
        raise SystemExit(f'Cannot split student name: {student.get("name")!r}')
    primary_phone = normalize_phone((student.get('phone') or {}).get('phone'))
    phone_list = [normalize_phone(item.get('phone')) for item in student.get('phoneList', []) if item.get('phone')]
    contact_list = [item.get('contactId') for item in student.get('contactList', []) if item.get('contactId')]
    additional_phones = [phone for phone in phone_list[1:] if phone]
    parent_phone = additional_phones[0] if additional_phones else None
    school = student.get('school') or {}
    balance = student.get('balance')
    created_at = earliest(subscription_dates_by_student.get(student['id']))
    created_at_sql = sql_ts_from_date(created_at) if created_at else NOW_SQL
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
            sql_str(None),
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
            created_at_sql,
            created_at_sql,
            '0',
            sql_str(BRANCH_ID),
            '0',
        ]) + ')'
    )

course_rows = []
for group in groups:
    group_id = group['id']
    base_price = max(subscription_amounts_by_group.get(group_id, [Decimal('0')]))
    created_at = earliest(subscription_dates_by_group.get(group_id))
    created_at_sql = sql_ts_from_date(created_at) if created_at else NOW_SQL
    course_rows.append(
        '(' + ', '.join([
            sql_str(group_id),
            sql_str(group.get('type') or 'GROUP'),
            sql_str('OFFLINE'),
            sql_str(group.get('name')),
            sql_str(group.get('description')),
            sql_num(base_price) if base_price else 'NULL',
            sql_num(group.get('limit')) if group.get('limit') is not None else 'NULL',
            sql_str(group.get('color')),
            sql_str('ACTIVE' if (group.get('status') or '').upper() == 'CURRENT' else 'INACTIVE'),
            sql_str(None),
            sql_str(None),
            created_at_sql,
            created_at_sql,
            '0',
            sql_str(BRANCH_ID),
        ]) + ')'
    )

course_student_rows = []
student_group_rows = []
for group in groups:
    group_id = group['id']
    for member in group.get('studentList', []):
        student_id = member.get('student', {}).get('id')
        if not student_id:
            continue
        pair_key = (group_id, student_id)
        pair_created_at = earliest(subscription_dates_by_pair.get(pair_key))
        pair_created_at_sql = sql_ts_from_date(pair_created_at) if pair_created_at else NOW_SQL
        course_student_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f'course_students:{group_id}:{student_id}'))
        student_group_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f'student_groups:{group_id}:{student_id}'))
        course_student_rows.append(
            '(' + ', '.join([
                sql_str(course_student_id),
                sql_str(group_id),
                sql_str(student_id),
                pair_created_at_sql,
                pair_created_at_sql,
                '0',
            ]) + ')'
        )
        student_group_rows.append(
            '(' + ', '.join([
                sql_str(student_group_id),
                sql_str(student_id),
                sql_str(group_id),
                sql_str('ACTIVE'),
                pair_created_at_sql,
                sql_str(None),
                sql_str(None),
                pair_created_at_sql,
                pair_created_at_sql,
                '0',
            ]) + ')'
        )

subscription_rows_sql = []
for row in subscriptions:
    purchase_date = date.fromisoformat(row['purchase_date']) if row['purchase_date'] else date.fromisoformat(row['start_date'])
    end_date = date.fromisoformat(row['end_date']) if row['end_date'] else None
    total_lessons = int(row['lesson_qty'])
    lessons_left = int(row['remaining'])
    amount = Decimal(row['total_price']) if row['total_price'] else Decimal('0')
    subscription_rows_sql.append(
        '(' + ', '.join([
            sql_str(row['id']),
            sql_str(row['student_id']),
            sql_str(row['group_id']),
            sql_str(row['group_id']),
            sql_str(None),
            sql_str(None),
            str(total_lessons),
            str(lessons_left),
            sql_date(date.fromisoformat(row['start_date'])) ,
            sql_date(end_date),
            sql_num(amount),
            sql_str('KZT'),
            sql_str('EXPIRED' if (row.get('status') or '').upper() == 'FINISHED' else 'ACTIVE'),
            sql_str(row.get('comment')),
            sql_ts_from_date(purchase_date),
            sql_ts_from_date(purchase_date),
            '0',
            sql_str(BRANCH_ID),
            '0',
        ]) + ')'
    )

emit_insert(
    f'{SCHEMA}.students',
    [
        'id', 'first_name', 'last_name', 'middle_name', 'customer', 'student_photo', 'email', 'phone', 'birth_date',
        'status', 'parent_name', 'parent_phone', 'student_phone', 'gender', 'address', 'city', 'notes', 'school',
        'grade', 'additional_info', 'contract', 'discount', 'comment', 'metadata', 'created_at', 'updated_at',
        'version', 'branch_id', 'discount_percent',
    ],
    student_rows,
    '(id)',
    [
        'first_name', 'last_name', 'middle_name', 'customer', 'student_photo', 'email', 'phone', 'birth_date',
        'status', 'parent_name', 'parent_phone', 'student_phone', 'gender', 'address', 'city', 'notes', 'school',
        'grade', 'additional_info', 'contract', 'discount', 'comment', 'metadata', 'updated_at', 'version', 'branch_id',
        'discount_percent',
    ],
)

emit_insert(
    f'{SCHEMA}.courses',
    [
        'id', 'type', 'format', 'name', 'description', 'base_price', 'enrollment_limit', 'color', 'status',
        'teacher_id', 'room_id', 'created_at', 'updated_at', 'version', 'branch_id',
    ],
    course_rows,
    '(id)',
    [
        'type', 'format', 'name', 'description', 'base_price', 'enrollment_limit', 'color', 'status', 'teacher_id',
        'room_id', 'updated_at', 'version', 'branch_id',
    ],
)

emit_insert(
    f'{SCHEMA}.course_students',
    ['id', 'course_id', 'student_id', 'created_at', 'updated_at', 'version'],
    course_student_rows,
    '(course_id, student_id)',
    ['id', 'created_at', 'updated_at', 'version'],
)

emit_insert(
    f'{SCHEMA}.student_groups',
    ['id', 'student_id', 'group_id', 'status', 'enrolled_at', 'completed_at', 'notes', 'created_at', 'updated_at', 'version'],
    student_group_rows,
    '(id)',
    ['student_id', 'group_id', 'status', 'enrolled_at', 'completed_at', 'notes', 'updated_at', 'version'],
)

emit_insert(
    f'{SCHEMA}.subscriptions',
    [
        'id', 'student_id', 'course_id', 'group_id', 'service_id', 'price_list_id', 'total_lessons', 'lessons_left',
        'start_date', 'end_date', 'amount', 'currency', 'status', 'notes', 'created_at', 'updated_at', 'version',
        'branch_id', 'discount_percent',
    ],
    subscription_rows_sql,
    '(id)',
    [
        'student_id', 'course_id', 'group_id', 'service_id', 'price_list_id', 'total_lessons', 'lessons_left',
        'start_date', 'end_date', 'amount', 'currency', 'status', 'notes', 'updated_at', 'version', 'branch_id',
        'discount_percent',
    ],
)

print('COMMIT;')
print(f'-- imported students={len(student_rows)} courses={len(course_rows)} course_students={len(course_student_rows)} student_groups={len(student_group_rows)} subscriptions={len(subscription_rows_sql)}', file=sys.stderr)
