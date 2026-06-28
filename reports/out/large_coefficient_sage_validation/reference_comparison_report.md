# Сверка с SageMath

Отчёт сравнивает локальные отчёты, совместимые с Android-частью, с результатами
SageMath, когда эталонные данные доступны.

## Статус

- Статус эталонной проверки: `available`
- Локальные отчёты: `reports/out/large_coefficient_curve_reports`
- Результаты Sage: `reports/out/large_coefficient_sage_validation/sage_reference_results.json`
- Сравнено кривых: 20
- Счётчики статусов: `{"match": 20}`

## Таблица сравнения

| curve_id | Статус | Несовпадения | Примечания |
| --- | --- | --- | --- |
| `lc128_controlled_full_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc128_large_a_small_b_no_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc128_large_pair_no_obvious_root` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc128_negative_mixed_no_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc128_scaled_order_four` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc32_controlled_full_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc32_controlled_one_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc32_mersenne_no_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc32_mixed_sign_one_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc32_prime_offsets_no_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc64_controlled_full_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc64_mixed_large_no_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc64_near_signed_limit_no_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc64_prime_offsets_no_two_torsion` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lc64_scaled_order_four` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lcstruct_full_two_torsion_large_three_roots` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lcstruct_lutz_large_discriminant_stress` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lcstruct_no_obvious_root_decimal_stress` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lcstruct_one_two_torsion_large_root` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |
| `lcstruct_scaled_order_four_medium` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля; size guard пропустил локальную Lutz-Nagell enumeration, при этом сопоставимые invariants и 2-torsion fields проверены. |

## Интерпретация

- `match` означает совпадение всех напрямую сопоставимых полей.
- `mismatch` требует отдельного просмотра перед использованием результата.
- `missing_reference` означает отсутствие SageMath output для этой кривой.
- `unsupported_local` или `unsupported_reference` фиксирует текущую область сравнения.
- `needs_manual_review` означает, что сравнение нельзя свести к прямому совпадению полей.
