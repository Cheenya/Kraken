# Бенчмарк диагностического backend

Бенчмарк показывает время работы диагностического backend на выбранных случаях.

- Прогревов на backend/case: 5
- Измерений на backend/case: 30
- Kotlin, сумма медиан: 109.3562 ms
- C++, сумма медиан: 5.8283 ms
- Ускорение C++ по сумме медиан: 18.7631x
- Сопоставимых exact-строк: 4
- Ускорение C++ на сопоставимых exact-строках: 54.5145x

| Случай | Группа | Kotlin median ms | C++ median ms | Ускорение | Kotlin case | C++ case |
|---|---:|---:|---:|---:|---|---|
| teaching_full_2_torsion | small_exact | 0.3215 | 0.0885 | 3.6315x | A6 | A6 |
| teaching_three_torsion_indicator | small_exact | 0.0385 | 0.0816 | 0.4718x | A2 | A2 |
| moderate_no_obvious_torsion | signed_128_exact | 0.4421 | 0.4543 | 0.9732x | SIZE_GUARDED | A4 |
| kotlin_divisor_scan_stress_b_semiprime | both_exact_stress | 40.5366 | 1.6173 | 25.0637x | A4 | A4 |
| kotlin_three_torsion_scan_stress | both_exact_stress | 67.9734 | 0.2096 | 324.3673x | A4 | A4 |
| large_smooth_a_2pow140 | smooth_bigint_exact | 0.0144 | 1.5959 | 0.0090x | SIZE_GUARDED | A4 |
| large_smooth_b_2pow130 | smooth_bigint_exact | 0.0296 | 1.7810 | 0.0166x | SIZE_GUARDED | A4 |
