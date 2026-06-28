# Сверка с SageMath

Отчёт сравнивает локальные отчёты, совместимые с Android-частью, с результатами
SageMath, когда эталонные данные доступны.

## Статус

- Статус эталонной проверки: `available`
- Локальные отчёты: `reports/out/android_curve_reports`
- Результаты Sage: `reports/out/sage_validation/sage_reference_results.json`
- Сравнено кривых: 15
- Счётчики статусов: `{"match": 11, "unsupported_local": 4}`

## Таблица сравнения

| curve_id | Статус | Несовпадения | Примечания |
| --- | --- | --- | --- |
| `fractional_full_two_torsion` | `unsupported_local` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `fractional_one_two_torsion` | `unsupported_local` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `full_two_torsion_scaled` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `full_two_torsion_three_roots` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `full_two_torsion_x3_minus_x` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `no_two_torsion_x3_plus_2x_plus_1` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `no_two_torsion_x3_plus_x_plus_1` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `nonsingular_negative_discriminant` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `one_two_torsion_plus_order_four` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `one_two_torsion_x3_minus_2x_plus_1` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `one_two_torsion_x3_minus_one` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `one_two_torsion_x3_plus_2x_plus_3` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `order_three_x3_plus_1` | `match` | - | Порядок кручения из SageMath используется как эталон; локальная bounded probing фиксирует сопоставимые диагностические поля. |
| `singular_double_root` | `unsupported_local` | - | Для сингулярной кривой rational roots не учитываются как torsion points эллиптической кривой. |
| `singular_zero_curve` | `unsupported_local` | - | Для сингулярной кривой rational roots не учитываются как torsion points эллиптической кривой. |

## Интерпретация

- `match` означает совпадение всех напрямую сопоставимых полей.
- `mismatch` требует отдельного просмотра перед использованием результата.
- `missing_reference` означает отсутствие SageMath output для этой кривой.
- `unsupported_local` или `unsupported_reference` фиксирует текущую область сравнения.
- `needs_manual_review` означает, что сравнение нельзя свести к прямому совпадению полей.
