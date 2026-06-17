#include <algorithm>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <jni.h>
#include <limits>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace {

using UInt128 = unsigned __int128;
using Int128 = __int128;

constexpr uint32_t kBigIntBase = 1000000000U;
constexpr int64_t kMaxDivisorEnumeration = 1000000LL;

struct Roots3Stats {
    int64_t candidates_total = 0;
    int64_t rejected_mod = 0;
    int64_t rejected_bound = 0;
    int64_t passed_filters = 0;
    int64_t exact_checked = 0;
    int64_t exact_zero = 0;
    int64_t squarecheck_pass = 0;
    int64_t divisor_count_a2 = 0;
    int64_t factorization_steps = 0;
    int64_t early_stop_hit = 0;
};

struct BigInt {
    bool negative = false;
    std::vector<uint32_t> limbs;

    bool is_zero() const {
        return limbs.empty();
    }

    void normalize() {
        while (!limbs.empty() && limbs.back() == 0U) {
            limbs.pop_back();
        }
        if (limbs.empty()) {
            negative = false;
        }
    }
};

struct SignedCandidate {
    bool negative = false;
    UInt128 magnitude = 0;
};

struct BigCandidate {
    bool negative = false;
    BigInt magnitude;
};

struct FactorizationResult {
    bool complete = true;
    int64_t steps = 0;
    std::vector<std::pair<UInt128, int>> factors;
};

struct BigFactorizationResult {
    bool complete = true;
    int64_t steps = 0;
    std::vector<std::pair<uint32_t, int>> factors;
};

std::string to_string_u128(UInt128 value) {
    if (value == 0) {
        return "0";
    }
    std::string out;
    while (value > 0) {
        const int digit = static_cast<int>(value % 10);
        out.push_back(static_cast<char>('0' + digit));
        value /= 10;
    }
    std::reverse(out.begin(), out.end());
    return out;
}

std::string to_string_i128(Int128 value) {
    if (value == 0) {
        return "0";
    }
    const bool negative = value < 0;
    const UInt128 magnitude = negative
        ? static_cast<UInt128>(-(value + 1)) + 1U
        : static_cast<UInt128>(value);
    return negative ? "-" + to_string_u128(magnitude) : to_string_u128(magnitude);
}

BigInt make_bigint_u128(UInt128 value) {
    BigInt out;
    while (value > 0) {
        out.limbs.push_back(static_cast<uint32_t>(value % kBigIntBase));
        value /= kBigIntBase;
    }
    out.normalize();
    return out;
}

BigInt make_bigint_i128(Int128 value) {
    if (value < 0) {
        BigInt out = make_bigint_u128(static_cast<UInt128>(-(value + 1)) + 1U);
        out.negative = !out.is_zero();
        return out;
    }
    return make_bigint_u128(static_cast<UInt128>(value));
}

BigInt make_bigint_candidate(const SignedCandidate& value) {
    BigInt out = make_bigint_u128(value.magnitude);
    out.negative = value.negative && !out.is_zero();
    return out;
}

BigInt make_bigint_candidate(const BigCandidate& value) {
    BigInt out = value.magnitude;
    out.negative = value.negative && !out.is_zero();
    return out;
}

bool parse_bigint_decimal(const std::string& raw, BigInt& out) {
    out = BigInt{};
    if (raw.empty()) {
        return false;
    }
    size_t index = 0;
    bool negative = false;
    if (raw[index] == '+' || raw[index] == '-') {
        negative = raw[index] == '-';
        ++index;
    }
    if (index >= raw.size()) {
        return false;
    }

    BigInt value;
    for (; index < raw.size(); ++index) {
        const char ch = raw[index];
        if (ch < '0' || ch > '9') {
            return false;
        }
        const uint64_t digit = static_cast<uint64_t>(ch - '0');
        uint64_t carry = digit;
        for (uint32_t& limb : value.limbs) {
            const uint64_t current = static_cast<uint64_t>(limb) * 10ULL + carry;
            limb = static_cast<uint32_t>(current % kBigIntBase);
            carry = current / kBigIntBase;
        }
        if (carry > 0) {
            value.limbs.push_back(static_cast<uint32_t>(carry));
        }
    }
    value.negative = negative;
    value.normalize();
    out = value;
    return true;
}

bool parse_i128_decimal(const std::string& raw, Int128& out) {
    if (raw.empty()) {
        return false;
    }
    size_t index = 0;
    bool negative = false;
    if (raw[index] == '+' || raw[index] == '-') {
        negative = raw[index] == '-';
        ++index;
    }
    if (index >= raw.size()) {
        return false;
    }

    const UInt128 positive_limit = ~static_cast<UInt128>(0) >> 1;
    const UInt128 negative_limit = positive_limit + 1U;
    const UInt128 limit = negative ? negative_limit : positive_limit;
    UInt128 value = 0;
    for (; index < raw.size(); ++index) {
        const char ch = raw[index];
        if (ch < '0' || ch > '9') {
            return false;
        }
        const UInt128 digit = static_cast<UInt128>(ch - '0');
        if (value > (limit - digit) / 10U) {
            return false;
        }
        value = value * 10U + digit;
    }

    if (negative) {
        out = value == negative_limit
            ? static_cast<Int128>(static_cast<UInt128>(1) << 127)
            : -static_cast<Int128>(value);
    } else {
        out = static_cast<Int128>(value);
    }
    return true;
}

int compare_abs(const BigInt& left, const BigInt& right) {
    if (left.limbs.size() != right.limbs.size()) {
        return left.limbs.size() < right.limbs.size() ? -1 : 1;
    }
    for (size_t index = left.limbs.size(); index > 0; --index) {
        const uint32_t a = left.limbs[index - 1];
        const uint32_t b = right.limbs[index - 1];
        if (a != b) {
            return a < b ? -1 : 1;
        }
    }
    return 0;
}

int compare_bigint(const BigInt& left, const BigInt& right) {
    if (left.negative != right.negative) {
        return left.negative ? -1 : 1;
    }
    const int abs_compare = compare_abs(left, right);
    return left.negative ? -abs_compare : abs_compare;
}

BigInt abs_bigint(BigInt value) {
    value.negative = false;
    value.normalize();
    return value;
}

BigInt add_abs(const BigInt& left, const BigInt& right) {
    BigInt out;
    const size_t size = std::max(left.limbs.size(), right.limbs.size());
    out.limbs.resize(size);
    uint64_t carry = 0;
    for (size_t index = 0; index < size; ++index) {
        const uint64_t a = index < left.limbs.size() ? left.limbs[index] : 0;
        const uint64_t b = index < right.limbs.size() ? right.limbs[index] : 0;
        const uint64_t current = a + b + carry;
        out.limbs[index] = static_cast<uint32_t>(current % kBigIntBase);
        carry = current / kBigIntBase;
    }
    if (carry > 0) {
        out.limbs.push_back(static_cast<uint32_t>(carry));
    }
    out.normalize();
    return out;
}

BigInt sub_abs(const BigInt& left, const BigInt& right) {
    BigInt out;
    out.limbs.resize(left.limbs.size());
    int64_t borrow = 0;
    for (size_t index = 0; index < left.limbs.size(); ++index) {
        int64_t current = static_cast<int64_t>(left.limbs[index]) - borrow;
        const int64_t b = index < right.limbs.size() ? right.limbs[index] : 0;
        current -= b;
        if (current < 0) {
            current += kBigIntBase;
            borrow = 1;
        } else {
            borrow = 0;
        }
        out.limbs[index] = static_cast<uint32_t>(current);
    }
    out.normalize();
    return out;
}

BigInt negate(BigInt value) {
    if (!value.is_zero()) {
        value.negative = !value.negative;
    }
    return value;
}

BigInt add_bigint(const BigInt& left, const BigInt& right) {
    BigInt out;
    if (left.negative == right.negative) {
        out = add_abs(left, right);
        out.negative = left.negative;
        out.normalize();
        return out;
    }
    const int cmp = compare_abs(left, right);
    if (cmp == 0) {
        return BigInt{};
    }
    if (cmp > 0) {
        out = sub_abs(left, right);
        out.negative = left.negative;
    } else {
        out = sub_abs(right, left);
        out.negative = right.negative;
    }
    out.normalize();
    return out;
}

BigInt sub_bigint(const BigInt& left, const BigInt& right) {
    return add_bigint(left, negate(right));
}

BigInt mul_bigint(const BigInt& left, const BigInt& right) {
    if (left.is_zero() || right.is_zero()) {
        return BigInt{};
    }
    std::vector<UInt128> temp(left.limbs.size() + right.limbs.size() + 1U, 0U);
    for (size_t i = 0; i < left.limbs.size(); ++i) {
        for (size_t j = 0; j < right.limbs.size(); ++j) {
            temp[i + j] += static_cast<UInt128>(left.limbs[i]) * static_cast<UInt128>(right.limbs[j]);
        }
    }
    BigInt out;
    out.limbs.resize(temp.size());
    UInt128 carry = 0;
    for (size_t index = 0; index < temp.size(); ++index) {
        const UInt128 current = temp[index] + carry;
        out.limbs[index] = static_cast<uint32_t>(current % kBigIntBase);
        carry = current / kBigIntBase;
    }
    while (carry > 0) {
        out.limbs.push_back(static_cast<uint32_t>(carry % kBigIntBase));
        carry /= kBigIntBase;
    }
    out.negative = left.negative != right.negative;
    out.normalize();
    return out;
}

BigInt mul_small(BigInt value, int64_t multiplier) {
    return mul_bigint(value, make_bigint_i128(static_cast<Int128>(multiplier)));
}

BigInt div2_positive(const BigInt& value) {
    BigInt out;
    out.limbs.resize(value.limbs.size());
    uint64_t remainder = 0;
    for (size_t index = value.limbs.size(); index > 0; --index) {
        const uint64_t current = remainder * kBigIntBase + value.limbs[index - 1];
        out.limbs[index - 1] = static_cast<uint32_t>(current / 2U);
        remainder = current % 2U;
    }
    out.normalize();
    return out;
}

uint32_t mod_small_abs(const BigInt& value, uint32_t divisor) {
    uint64_t remainder = 0;
    for (size_t index = value.limbs.size(); index > 0; --index) {
        const uint64_t current = remainder * kBigIntBase + value.limbs[index - 1];
        remainder = current % divisor;
    }
    return static_cast<uint32_t>(remainder);
}

BigInt div_small_abs(const BigInt& value, uint32_t divisor) {
    BigInt out;
    out.limbs.resize(value.limbs.size());
    uint64_t remainder = 0;
    for (size_t index = value.limbs.size(); index > 0; --index) {
        const uint64_t current = remainder * kBigIntBase + value.limbs[index - 1];
        out.limbs[index - 1] = static_cast<uint32_t>(current / divisor);
        remainder = current % divisor;
    }
    out.normalize();
    return out;
}

std::string to_string_bigint(const BigInt& value) {
    if (value.is_zero()) {
        return "0";
    }
    std::ostringstream out;
    if (value.negative) {
        out << '-';
    }
    out << value.limbs.back();
    for (size_t index = value.limbs.size() - 1; index > 0; --index) {
        out.width(9);
        out.fill('0');
        out << value.limbs[index - 1];
    }
    return out.str();
}

BigInt discriminant_bigint(const BigInt& a, const BigInt& b) {
    const BigInt a2 = mul_bigint(a, a);
    const BigInt a3 = mul_bigint(a2, a);
    const BigInt b2 = mul_bigint(b, b);
    const BigInt inside = add_bigint(mul_small(a3, 4), mul_small(b2, 27));
    return mul_small(inside, -16);
}

BigInt poly_f_bigint(const BigInt& a, const BigInt& b, const SignedCandidate& x_raw) {
    const BigInt x = make_bigint_candidate(x_raw);
    const BigInt x2 = mul_bigint(x, x);
    const BigInt x3 = mul_bigint(x2, x);
    return add_bigint(add_bigint(x3, mul_bigint(a, x)), b);
}

BigInt poly_psi3_bigint(const BigInt& a, const BigInt& b, const SignedCandidate& x_raw) {
    const BigInt x = make_bigint_candidate(x_raw);
    const BigInt x2 = mul_bigint(x, x);
    const BigInt x4 = mul_bigint(x2, x2);
    BigInt out = mul_small(x4, 3);
    out = add_bigint(out, mul_small(mul_bigint(a, x2), 6));
    out = add_bigint(out, mul_small(mul_bigint(b, x), 12));
    out = sub_bigint(out, mul_bigint(a, a));
    return out;
}

BigInt poly_f_bigint(const BigInt& a, const BigInt& b, const BigCandidate& x_raw) {
    const BigInt x = make_bigint_candidate(x_raw);
    const BigInt x2 = mul_bigint(x, x);
    const BigInt x3 = mul_bigint(x2, x);
    return add_bigint(add_bigint(x3, mul_bigint(a, x)), b);
}

BigInt poly_psi3_bigint(const BigInt& a, const BigInt& b, const BigCandidate& x_raw) {
    const BigInt x = make_bigint_candidate(x_raw);
    const BigInt x2 = mul_bigint(x, x);
    const BigInt x4 = mul_bigint(x2, x2);
    BigInt out = mul_small(x4, 3);
    out = add_bigint(out, mul_small(mul_bigint(a, x2), 6));
    out = add_bigint(out, mul_small(mul_bigint(b, x), 12));
    out = sub_bigint(out, mul_bigint(a, a));
    return out;
}

bool is_square_nonnegative_bigint(const BigInt& value) {
    if (value.negative) {
        return false;
    }
    if (value.is_zero()) {
        return true;
    }

    BigInt low;
    BigInt high = value;
    const BigInt one = make_bigint_i128(1);
    while (compare_bigint(low, high) <= 0) {
        const BigInt mid = div2_positive(add_bigint(low, high));
        const BigInt square = mul_bigint(mid, mid);
        const int cmp = compare_bigint(square, value);
        if (cmp == 0) {
            return true;
        }
        if (cmp < 0) {
            low = add_bigint(mid, one);
        } else {
            if (mid.is_zero()) {
                return false;
            }
            high = sub_bigint(mid, one);
        }
    }
    return false;
}

bool sqrt_nonnegative_bigint(const BigInt& value, BigInt& root) {
    root = BigInt{};
    if (value.negative) {
        return false;
    }
    if (value.is_zero()) {
        return true;
    }

    BigInt low;
    BigInt high = value;
    const BigInt one = make_bigint_i128(1);
    while (compare_bigint(low, high) <= 0) {
        const BigInt mid = div2_positive(add_bigint(low, high));
        const BigInt square = mul_bigint(mid, mid);
        const int cmp = compare_bigint(square, value);
        if (cmp == 0) {
            root = mid;
            return true;
        }
        if (cmp < 0) {
            low = add_bigint(mid, one);
        } else {
            if (mid.is_zero()) {
                return false;
            }
            high = sub_bigint(mid, one);
        }
    }
    return false;
}

UInt128 abs_u128(Int128 value) {
    return value < 0
        ? static_cast<UInt128>(-(value + 1)) + 1U
        : static_cast<UInt128>(value);
}

long double to_long_double_u128(UInt128 value) {
    constexpr UInt128 chunk_base = static_cast<UInt128>(1000000000000000000ULL);
    long double out = 0.0L;
    long double multiplier = 1.0L;
    while (value > 0) {
        const uint64_t chunk = static_cast<uint64_t>(value % chunk_base);
        out += static_cast<long double>(chunk) * multiplier;
        multiplier *= 1000000000000000000.0L;
        value /= chunk_base;
    }
    return out;
}

UInt128 ceil_to_u128(long double value) {
    if (value <= 0.0L) {
        return 0;
    }
    const long double max_u128 = to_long_double_u128(~static_cast<UInt128>(0));
    if (value >= max_u128) {
        return ~static_cast<UInt128>(0);
    }
    return static_cast<UInt128>(std::ceil(value));
}

UInt128 psi3_nonroot_limit(Int128 a, Int128 b) {
    const long double aa = to_long_double_u128(abs_u128(a));
    const long double bb = to_long_double_u128(abs_u128(b));
    const long double l1 = std::sqrt(12.0L * aa);
    const long double l2 = std::cbrt(24.0L * bb);
    const long double l3 = std::pow(2.0L * aa * aa, 0.25L);
    return ceil_to_u128(std::max({l1, l2, l3}) + 1.0L);
}

UInt128 mod_add(UInt128 a, UInt128 b, UInt128 mod) {
    a %= mod;
    b %= mod;
    return a >= mod - b ? a - (mod - b) : a + b;
}

UInt128 mod_mul(UInt128 a, UInt128 b, UInt128 mod) {
    UInt128 out = 0;
    a %= mod;
    while (b > 0) {
        if ((b & 1U) != 0) {
            out = mod_add(out, a, mod);
        }
        b >>= 1U;
        if (b > 0) {
            a = mod_add(a, a, mod);
        }
    }
    return out;
}

UInt128 mod_pow(UInt128 base, UInt128 exponent, UInt128 mod) {
    UInt128 out = 1 % mod;
    base %= mod;
    while (exponent > 0) {
        if ((exponent & 1U) != 0) {
            out = mod_mul(out, base, mod);
        }
        exponent >>= 1U;
        if (exponent > 0) {
            base = mod_mul(base, base, mod);
        }
    }
    return out;
}

UInt128 gcd_u128(UInt128 a, UInt128 b) {
    while (b != 0) {
        const UInt128 r = a % b;
        a = b;
        b = r;
    }
    return a;
}

UInt128 sqrt_floor_u128(UInt128 value) {
    UInt128 low = 0;
    UInt128 high = static_cast<UInt128>(1) << 64;
    UInt128 answer = 0;
    while (low <= high) {
        const UInt128 mid = low + ((high - low) >> 1U);
        const UInt128 square = mid * mid;
        if (square <= value) {
            answer = mid;
            low = mid + 1U;
        } else {
            if (mid == 0) {
                break;
            }
            high = mid - 1U;
        }
    }
    return answer;
}

bool is_square_u128(UInt128 value, UInt128& root) {
    root = sqrt_floor_u128(value);
    return root * root == value;
}

const std::vector<int>& trial_primes() {
    static const std::vector<int> primes = [] {
        constexpr int limit = 10000;
        std::vector<bool> is_prime(limit + 1, true);
        is_prime[0] = false;
        is_prime[1] = false;
        for (int p = 2; p * p <= limit; ++p) {
            if (!is_prime[p]) {
                continue;
            }
            for (int candidate = p * p; candidate <= limit; candidate += p) {
                is_prime[candidate] = false;
            }
        }
        std::vector<int> out;
        for (int index = 2; index <= limit; ++index) {
            if (is_prime[index]) {
                out.push_back(index);
            }
        }
        return out;
    }();
    return primes;
}

bool is_probable_prime(UInt128 n) {
    if (n < 2) {
        return false;
    }
    for (const int p : trial_primes()) {
        const UInt128 prime = static_cast<UInt128>(p);
        if (n == prime) {
            return true;
        }
        if (n % prime == 0) {
            return false;
        }
    }

    UInt128 d = n - 1;
    int s = 0;
    while ((d & 1U) == 0) {
        d >>= 1U;
        ++s;
    }

    const uint64_t bases[] = {
        2ULL, 3ULL, 5ULL, 7ULL, 11ULL, 13ULL, 17ULL, 19ULL,
        23ULL, 29ULL, 31ULL, 37ULL, 41ULL, 43ULL, 47ULL, 53ULL,
    };
    for (const uint64_t base_raw : bases) {
        if (static_cast<UInt128>(base_raw) >= n) {
            continue;
        }
        UInt128 x = mod_pow(static_cast<UInt128>(base_raw), d, n);
        if (x == 1 || x == n - 1) {
            continue;
        }
        bool witness = true;
        for (int r = 1; r < s; ++r) {
            x = mod_mul(x, x, n);
            if (x == n - 1) {
                witness = false;
                break;
            }
        }
        if (witness) {
            return false;
        }
    }
    return true;
}

UInt128 pollard_rho(UInt128 n, int64_t& steps) {
    if ((n & 1U) == 0) {
        return 2;
    }
    for (UInt128 c = 1; c < 128; ++c) {
        UInt128 x = 2 + c;
        UInt128 y = x;
        UInt128 d = 1;
        for (int iteration = 0; iteration < 200000 && d == 1; ++iteration) {
            x = mod_add(mod_mul(x, x, n), c, n);
            y = mod_add(mod_mul(y, y, n), c, n);
            y = mod_add(mod_mul(y, y, n), c, n);
            const UInt128 diff = x > y ? x - y : y - x;
            d = gcd_u128(diff, n);
            ++steps;
        }
        if (d > 1 && d < n) {
            return d;
        }
    }
    return 0;
}

void factor_recursive(UInt128 n, FactorizationResult& result) {
    if (!result.complete || n <= 1) {
        return;
    }
    for (const int p : trial_primes()) {
        const UInt128 prime = static_cast<UInt128>(p);
        if (prime * prime > n) {
            break;
        }
        if (n % prime == 0) {
            int exponent = 0;
            while (n % prime == 0) {
                n /= prime;
                ++exponent;
                ++result.steps;
            }
            result.factors.push_back({prime, exponent});
            factor_recursive(n, result);
            return;
        }
        ++result.steps;
    }
    if (n <= 1) {
        return;
    }
    if (is_probable_prime(n)) {
        result.factors.push_back({n, 1});
        return;
    }
    const UInt128 divisor = pollard_rho(n, result.steps);
    if (divisor == 0 || divisor == n) {
        result.complete = false;
        return;
    }
    factor_recursive(divisor, result);
    factor_recursive(n / divisor, result);
}

FactorizationResult factorize_u128(UInt128 n) {
    FactorizationResult result;
    factor_recursive(n, result);
    if (!result.complete) {
        return result;
    }
    std::sort(result.factors.begin(), result.factors.end(), [](const auto& left, const auto& right) {
        return left.first < right.first;
    });
    std::vector<std::pair<UInt128, int>> compact;
    for (const auto& factor : result.factors) {
        if (!compact.empty() && compact.back().first == factor.first) {
            compact.back().second += factor.second;
        } else {
            compact.push_back(factor);
        }
    }
    result.factors = compact;
    return result;
}

BigFactorizationResult factorize_bigint_smooth(BigInt value) {
    BigFactorizationResult result;
    value = abs_bigint(value);
    if (value.is_zero()) {
        return result;
    }

    for (const int p_raw : trial_primes()) {
        const uint32_t p = static_cast<uint32_t>(p_raw);
        int exponent = 0;
        while (!value.is_zero() && mod_small_abs(value, p) == 0U) {
            value = div_small_abs(value, p);
            ++exponent;
            ++result.steps;
        }
        if (exponent > 0) {
            result.factors.push_back({p, exponent});
        } else {
            ++result.steps;
        }
        if (compare_bigint(value, make_bigint_i128(1)) == 0) {
            return result;
        }
    }

    if (compare_bigint(value, make_bigint_i128(1)) != 0) {
        result.complete = false;
    }
    return result;
}

void generate_divisors_recursive(
    const std::vector<std::pair<UInt128, int>>& factors,
    size_t index,
    UInt128 current,
    UInt128 bound,
    std::vector<UInt128>& out,
    bool& complete
) {
    if (!complete) {
        return;
    }
    if (index == factors.size()) {
        out.push_back(current);
        if (out.size() > static_cast<size_t>(kMaxDivisorEnumeration)) {
            complete = false;
        }
        return;
    }
    const auto& [prime, exponent] = factors[index];
    UInt128 power = 1;
    for (int i = 0; i <= exponent; ++i) {
        if (current <= bound / power) {
            generate_divisors_recursive(factors, index + 1, current * power, bound, out, complete);
        }
        if (i == exponent || power > bound / prime) {
            break;
        }
        power *= prime;
    }
}

bool positive_divisors_bounded(
    std::vector<std::pair<UInt128, int>> factors,
    int exp_multiplier,
    UInt128 bound,
    std::vector<UInt128>& out
) {
    for (auto& factor : factors) {
        factor.second *= exp_multiplier;
    }
    bool complete = true;
    out.clear();
    generate_divisors_recursive(factors, 0, 1, bound, out, complete);
    if (!complete) {
        return false;
    }
    std::sort(out.begin(), out.end());
    out.erase(std::unique(out.begin(), out.end()), out.end());
    return true;
}

void generate_big_divisors_recursive(
    const std::vector<std::pair<uint32_t, int>>& factors,
    size_t index,
    BigInt current,
    std::vector<BigInt>& out,
    bool& complete
) {
    if (!complete) {
        return;
    }
    if (index == factors.size()) {
        out.push_back(current);
        if (out.size() > static_cast<size_t>(kMaxDivisorEnumeration)) {
            complete = false;
        }
        return;
    }
    const auto& [prime, exponent] = factors[index];
    BigInt power = make_bigint_i128(1);
    for (int i = 0; i <= exponent; ++i) {
        generate_big_divisors_recursive(factors, index + 1, mul_bigint(current, power), out, complete);
        if (!complete || i == exponent) {
            break;
        }
        power = mul_small(power, prime);
    }
}

bool positive_big_divisors_bounded(
    std::vector<std::pair<uint32_t, int>> factors,
    int exp_multiplier,
    std::vector<BigInt>& out
) {
    for (auto& factor : factors) {
        factor.second *= exp_multiplier;
    }
    bool complete = true;
    out.clear();
    generate_big_divisors_recursive(factors, 0, make_bigint_i128(1), out, complete);
    if (!complete) {
        return false;
    }
    std::sort(out.begin(), out.end(), [](const BigInt& left, const BigInt& right) {
        return compare_bigint(left, right) < 0;
    });
    out.erase(std::unique(out.begin(), out.end(), [](const BigInt& left, const BigInt& right) {
        return compare_bigint(left, right) == 0;
    }), out.end());
    return true;
}

SignedCandidate make_candidate(UInt128 magnitude, bool negative) {
    return SignedCandidate{negative && magnitude != 0, magnitude};
}

std::string to_string_candidate(const SignedCandidate& candidate) {
    return candidate.negative ? "-" + to_string_u128(candidate.magnitude) : to_string_u128(candidate.magnitude);
}

std::vector<SignedCandidate> signed_candidates_from_positive_divisors(
    const std::vector<UInt128>& positive_divisors,
    bool include_zero
) {
    std::vector<SignedCandidate> out;
    if (include_zero) {
        out.push_back(make_candidate(0, false));
    }
    for (const UInt128 divisor : positive_divisors) {
        if (divisor == 0) {
            continue;
        }
        out.push_back(make_candidate(divisor, false));
        out.push_back(make_candidate(divisor, true));
    }
    return out;
}

std::string join_csv_candidates(const std::vector<SignedCandidate>& values) {
    if (values.empty()) {
        return "-";
    }
    std::ostringstream out;
    for (size_t index = 0; index < values.size(); ++index) {
        if (index > 0) {
            out << ',';
        }
        out << to_string_candidate(values[index]);
    }
    return out.str();
}

BigCandidate make_big_candidate(BigInt magnitude, bool negative) {
    magnitude.negative = false;
    magnitude.normalize();
    return BigCandidate{negative && !magnitude.is_zero(), magnitude};
}

std::string to_string_candidate(const BigCandidate& candidate) {
    const std::string magnitude = to_string_bigint(candidate.magnitude);
    return candidate.negative && magnitude != "0" ? "-" + magnitude : magnitude;
}

std::vector<BigCandidate> signed_candidates_from_positive_big_divisors(
    const std::vector<BigInt>& positive_divisors,
    bool include_zero
) {
    std::vector<BigCandidate> out;
    if (include_zero) {
        out.push_back(make_big_candidate(BigInt{}, false));
    }
    for (BigInt divisor : positive_divisors) {
        divisor.negative = false;
        divisor.normalize();
        if (divisor.is_zero()) {
            continue;
        }
        out.push_back(make_big_candidate(divisor, false));
        out.push_back(make_big_candidate(divisor, true));
    }
    return out;
}

std::string join_csv_candidates(const std::vector<BigCandidate>& values) {
    if (values.empty()) {
        return "-";
    }
    std::ostringstream out;
    for (size_t index = 0; index < values.size(); ++index) {
        if (index > 0) {
            out << ',';
        }
        out << to_string_candidate(values[index]);
    }
    return out.str();
}

int mod_norm_candidate(const SignedCandidate& value, int prime) {
    const int magnitude = static_cast<int>(value.magnitude % static_cast<UInt128>(prime));
    if (!value.negative || magnitude == 0) {
        return magnitude;
    }
    return prime - magnitude;
}

int mod_norm_candidate(const BigCandidate& value, int prime) {
    const int magnitude = static_cast<int>(mod_small_abs(value.magnitude, static_cast<uint32_t>(prime)));
    if (!value.negative || magnitude == 0) {
        return magnitude;
    }
    return prime - magnitude;
}

int mod_norm_i128(Int128 value, int prime) {
    const int magnitude = static_cast<int>(abs_u128(value) % static_cast<UInt128>(prime));
    if (value >= 0 || magnitude == 0) {
        return magnitude;
    }
    return prime - magnitude;
}

int psi3_mod(Int128 a, Int128 b, const SignedCandidate& x, int prime) {
    const int xp = mod_norm_candidate(x, prime);
    const int ap = mod_norm_i128(a, prime);
    const int bp = mod_norm_i128(b, prime);
    const int64_t x2 = (static_cast<int64_t>(xp) * xp) % prime;
    const int64_t x4 = (x2 * x2) % prime;
    const int64_t t1 = (3LL * x4) % prime;
    const int64_t t2 = (6LL * ap * x2) % prime;
    const int64_t t3 = (12LL * bp * xp) % prime;
    const int64_t t4 = (static_cast<int64_t>(ap) * ap) % prime;
    int64_t out = (t1 + t2 + t3 - t4) % prime;
    if (out < 0) {
        out += prime;
    }
    return static_cast<int>(out);
}

int mod_norm_bigint(const BigInt& value, int prime) {
    const int magnitude = static_cast<int>(mod_small_abs(value, static_cast<uint32_t>(prime)));
    if (!value.negative || magnitude == 0) {
        return magnitude;
    }
    return prime - magnitude;
}

int psi3_mod(const BigInt& a, const BigInt& b, const BigCandidate& x, int prime) {
    const int xp = mod_norm_candidate(x, prime);
    const int ap = mod_norm_bigint(a, prime);
    const int bp = mod_norm_bigint(b, prime);
    const int64_t x2 = (static_cast<int64_t>(xp) * xp) % prime;
    const int64_t x4 = (x2 * x2) % prime;
    const int64_t t1 = (3LL * x4) % prime;
    const int64_t t2 = (6LL * ap * x2) % prime;
    const int64_t t3 = (12LL * bp * xp) % prime;
    const int64_t t4 = (static_cast<int64_t>(ap) * ap) % prime;
    int64_t out = (t1 + t2 + t3 - t4) % prime;
    if (out < 0) {
        out += prime;
    }
    return static_cast<int>(out);
}

std::string classify_case(bool has3, int two_torsion_count) {
    if (has3 && two_torsion_count == 0) return "A1";
    if (has3 && two_torsion_count == 1) return "A2";
    if (has3 && two_torsion_count == 3) return "A3";
    if (!has3 && two_torsion_count == 0) return "A4";
    if (!has3 && two_torsion_count == 1) return "A5";
    if (!has3 && two_torsion_count == 3) return "A6";
    return "NA";
}

std::string guarded_result(const BigInt& a, const BigInt& b, const BigInt& discriminant, bool singular) {
    std::ostringstream out;
    out << "ok" << '\t'
        << to_string_bigint(a) << '\t'
        << to_string_bigint(b) << '\t'
        << (singular ? 1 : 0) << '\t'
        << to_string_bigint(discriminant) << '\t'
        << 0 << '\t'
        << "-" << '\t'
        << 0 << '\t'
        << "-" << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << (singular ? "SINGULAR" : "SIZE_GUARDED") << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << 0 << '\t'
        << "-" << '\t'
        << 0;
    return out.str();
}

std::string classify_adamova_v3_i128(Int128 a_raw, Int128 b_raw) {
    const BigInt a = make_bigint_i128(a_raw);
    const BigInt b = make_bigint_i128(b_raw);
    const BigInt discriminant = discriminant_bigint(a, b);
    const bool singular = discriminant.is_zero();

    std::vector<SignedCandidate> roots2;
    std::vector<SignedCandidate> roots3;
    std::vector<SignedCandidate> x_square;
    Roots3Stats stats;
    int has3_strict = 0;
    int has3_inconsistent = 0;
    std::string adamova_case = "-";

    if (!singular) {
        const UInt128 abs_b = abs_u128(b_raw);
        std::vector<UInt128> divisors_b;
        if (abs_b == 0) {
            divisors_b = {0};
        } else {
            FactorizationResult factor_b = factorize_u128(abs_b);
            stats.factorization_steps += factor_b.steps;
            if (!factor_b.complete || !positive_divisors_bounded(factor_b.factors, 1, abs_b, divisors_b)) {
                return guarded_result(a, b, discriminant, false);
            }
        }

        std::vector<SignedCandidate> root2_candidates;
        if (abs_b == 0) {
            root2_candidates.push_back(make_candidate(0, false));
            if (a_raw < 0) {
                UInt128 root = 0;
                if (is_square_u128(abs_u128(a_raw), root) && root != 0) {
                    root2_candidates.push_back(make_candidate(root, false));
                    root2_candidates.push_back(make_candidate(root, true));
                }
            }
        } else {
            root2_candidates = signed_candidates_from_positive_divisors(divisors_b, false);
        }
        for (const SignedCandidate& x : root2_candidates) {
            if (poly_f_bigint(a, b, x).is_zero()) {
                roots2.push_back(x);
            }
        }

        const UInt128 abs_a = abs_u128(a_raw);
        const UInt128 nonroot_limit = psi3_nonroot_limit(a_raw, b_raw);
        std::vector<UInt128> divisors_a2;
        if (abs_a == 0) {
            divisors_a2 = {0};
        } else {
            FactorizationResult factor_a = factorize_u128(abs_a);
            stats.factorization_steps += factor_a.steps;
            if (!factor_a.complete || !positive_divisors_bounded(factor_a.factors, 2, nonroot_limit, divisors_a2)) {
                return guarded_result(a, b, discriminant, false);
            }
        }
        stats.divisor_count_a2 = static_cast<int64_t>(divisors_a2.size());

        const int mods[] = {5, 7, 11};
        std::vector<SignedCandidate> root3_candidates = signed_candidates_from_positive_divisors(
            divisors_a2,
            abs_a == 0
        );
        for (const SignedCandidate& x : root3_candidates) {
            ++stats.candidates_total;
            if (x.magnitude > nonroot_limit) {
                ++stats.rejected_bound;
                continue;
            }

            bool mod_ok = true;
            for (const int mod : mods) {
                if (psi3_mod(a_raw, b_raw, x, mod) != 0) {
                    mod_ok = false;
                    break;
                }
            }
            if (!mod_ok) {
                ++stats.rejected_mod;
                continue;
            }

            ++stats.exact_checked;
            if (poly_psi3_bigint(a, b, x).is_zero()) {
                ++stats.exact_zero;
                roots3.push_back(x);
                if (is_square_nonnegative_bigint(poly_f_bigint(a, b, x))) {
                    ++stats.squarecheck_pass;
                    x_square.push_back(x);
                }
            }
        }

        stats.passed_filters = stats.candidates_total - stats.rejected_mod - stats.rejected_bound;
        if (stats.passed_filters < 0) {
            stats.passed_filters = 0;
        }
        has3_strict = x_square.empty() ? 0 : 1;
        has3_inconsistent = (has3_strict == 1 && mod_norm_i128(a_raw, 3) != 0) ? 1 : 0;
        adamova_case = classify_case(has3_strict == 1, static_cast<int>(roots2.size()));
    }

    auto sort_candidates = [](std::vector<SignedCandidate>& values) {
        std::sort(values.begin(), values.end(), [](const SignedCandidate& left, const SignedCandidate& right) {
            if (left.negative != right.negative) {
                return left.negative;
            }
            if (left.magnitude == right.magnitude) {
                return false;
            }
            return left.negative ? left.magnitude > right.magnitude : left.magnitude < right.magnitude;
        });
        values.erase(std::unique(values.begin(), values.end(), [](const auto& left, const auto& right) {
            return left.negative == right.negative && left.magnitude == right.magnitude;
        }), values.end());
    };
    sort_candidates(roots2);
    sort_candidates(roots3);
    sort_candidates(x_square);

    std::ostringstream out;
    out << "ok" << '\t'
        << to_string_i128(a_raw) << '\t'
        << to_string_i128(b_raw) << '\t'
        << (singular ? 1 : 0) << '\t'
        << to_string_bigint(discriminant) << '\t'
        << roots2.size() << '\t'
        << join_csv_candidates(roots2) << '\t'
        << roots3.size() << '\t'
        << join_csv_candidates(roots3) << '\t'
        << has3_strict << '\t'
        << has3_inconsistent << '\t'
        << adamova_case << '\t'
        << stats.candidates_total << '\t'
        << stats.rejected_mod << '\t'
        << stats.rejected_bound << '\t'
        << stats.passed_filters << '\t'
        << stats.exact_checked << '\t'
        << stats.exact_zero << '\t'
        << stats.squarecheck_pass << '\t'
        << stats.divisor_count_a2 << '\t'
        << stats.factorization_steps << '\t'
        << join_csv_candidates(x_square) << '\t'
        << stats.early_stop_hit;
    return out.str();
}

std::string classify_adamova_v3_bigint_smooth(const BigInt& a, const BigInt& b) {
    const BigInt discriminant = discriminant_bigint(a, b);
    const bool singular = discriminant.is_zero();

    std::vector<BigCandidate> roots2;
    std::vector<BigCandidate> roots3;
    std::vector<BigCandidate> x_square;
    Roots3Stats stats;
    int has3_strict = 0;
    int has3_inconsistent = 0;
    std::string adamova_case = "-";

    if (!singular) {
        const BigInt abs_b = abs_bigint(b);
        std::vector<BigInt> divisors_b;
        if (abs_b.is_zero()) {
            divisors_b = {BigInt{}};
        } else {
            BigFactorizationResult factor_b = factorize_bigint_smooth(abs_b);
            stats.factorization_steps += factor_b.steps;
            if (!factor_b.complete || !positive_big_divisors_bounded(factor_b.factors, 1, divisors_b)) {
                return guarded_result(a, b, discriminant, false);
            }
        }

        std::vector<BigCandidate> root2_candidates;
        if (abs_b.is_zero()) {
            root2_candidates.push_back(make_big_candidate(BigInt{}, false));
            if (a.negative) {
                BigInt root;
                if (sqrt_nonnegative_bigint(abs_bigint(a), root) && !root.is_zero()) {
                    root2_candidates.push_back(make_big_candidate(root, false));
                    root2_candidates.push_back(make_big_candidate(root, true));
                }
            }
        } else {
            root2_candidates = signed_candidates_from_positive_big_divisors(divisors_b, false);
        }
        for (const BigCandidate& x : root2_candidates) {
            if (poly_f_bigint(a, b, x).is_zero()) {
                roots2.push_back(x);
            }
        }

        const BigInt abs_a = abs_bigint(a);
        std::vector<BigInt> divisors_a2;
        if (abs_a.is_zero()) {
            divisors_a2 = {BigInt{}};
        } else {
            BigFactorizationResult factor_a = factorize_bigint_smooth(abs_a);
            stats.factorization_steps += factor_a.steps;
            if (!factor_a.complete || !positive_big_divisors_bounded(factor_a.factors, 2, divisors_a2)) {
                return guarded_result(a, b, discriminant, false);
            }
        }
        stats.divisor_count_a2 = static_cast<int64_t>(divisors_a2.size());

        const int mods[] = {5, 7, 11};
        std::vector<BigCandidate> root3_candidates = signed_candidates_from_positive_big_divisors(
            divisors_a2,
            abs_a.is_zero()
        );
        for (const BigCandidate& x : root3_candidates) {
            ++stats.candidates_total;

            bool mod_ok = true;
            for (const int mod : mods) {
                if (psi3_mod(a, b, x, mod) != 0) {
                    mod_ok = false;
                    break;
                }
            }
            if (!mod_ok) {
                ++stats.rejected_mod;
                continue;
            }

            ++stats.exact_checked;
            if (poly_psi3_bigint(a, b, x).is_zero()) {
                ++stats.exact_zero;
                roots3.push_back(x);
                if (is_square_nonnegative_bigint(poly_f_bigint(a, b, x))) {
                    ++stats.squarecheck_pass;
                    x_square.push_back(x);
                }
            }
        }

        stats.passed_filters = stats.candidates_total - stats.rejected_mod - stats.rejected_bound;
        if (stats.passed_filters < 0) {
            stats.passed_filters = 0;
        }
        has3_strict = x_square.empty() ? 0 : 1;
        has3_inconsistent = (has3_strict == 1 && mod_norm_bigint(a, 3) != 0) ? 1 : 0;
        adamova_case = classify_case(has3_strict == 1, static_cast<int>(roots2.size()));
    }

    auto sort_candidates = [](std::vector<BigCandidate>& values) {
        std::sort(values.begin(), values.end(), [](const BigCandidate& left, const BigCandidate& right) {
            if (left.negative != right.negative) {
                return left.negative;
            }
            const int cmp = compare_bigint(left.magnitude, right.magnitude);
            if (cmp == 0) {
                return false;
            }
            return left.negative ? cmp > 0 : cmp < 0;
        });
        values.erase(std::unique(values.begin(), values.end(), [](const auto& left, const auto& right) {
            return left.negative == right.negative && compare_bigint(left.magnitude, right.magnitude) == 0;
        }), values.end());
    };
    sort_candidates(roots2);
    sort_candidates(roots3);
    sort_candidates(x_square);

    std::ostringstream out;
    out << "ok" << '\t'
        << to_string_bigint(a) << '\t'
        << to_string_bigint(b) << '\t'
        << (singular ? 1 : 0) << '\t'
        << to_string_bigint(discriminant) << '\t'
        << roots2.size() << '\t'
        << join_csv_candidates(roots2) << '\t'
        << roots3.size() << '\t'
        << join_csv_candidates(roots3) << '\t'
        << has3_strict << '\t'
        << has3_inconsistent << '\t'
        << adamova_case << '\t'
        << stats.candidates_total << '\t'
        << stats.rejected_mod << '\t'
        << stats.rejected_bound << '\t'
        << stats.passed_filters << '\t'
        << stats.exact_checked << '\t'
        << stats.exact_zero << '\t'
        << stats.squarecheck_pass << '\t'
        << stats.divisor_count_a2 << '\t'
        << stats.factorization_steps << '\t'
        << join_csv_candidates(x_square) << '\t'
        << stats.early_stop_hit;
    return out.str();
}

std::string classify_adamova_v3_decimal(const std::string& a_raw, const std::string& b_raw) {
    Int128 a = 0;
    Int128 b = 0;
    if (parse_i128_decimal(a_raw, a) && parse_i128_decimal(b_raw, b)) {
        return classify_adamova_v3_i128(a, b);
    }

    BigInt big_a;
    BigInt big_b;
    if (!parse_bigint_decimal(a_raw, big_a) || !parse_bigint_decimal(b_raw, big_b)) {
        return "unsupported\tcoefficient outside native decimal parser range";
    }
    return classify_adamova_v3_bigint_smooth(big_a, big_b);
}

jstring classify_decimal_jni(JNIEnv* env, jstring a_raw, jstring b_raw) {
    if (a_raw == nullptr || b_raw == nullptr) {
        return env->NewStringUTF("unsupported\tmissing coefficient string");
    }
    const char* a_chars = env->GetStringUTFChars(a_raw, nullptr);
    const char* b_chars = env->GetStringUTFChars(b_raw, nullptr);
    if (a_chars == nullptr || b_chars == nullptr) {
        if (a_chars != nullptr) {
            env->ReleaseStringUTFChars(a_raw, a_chars);
        }
        if (b_chars != nullptr) {
            env->ReleaseStringUTFChars(b_raw, b_chars);
        }
        return env->NewStringUTF("unsupported\tcoefficient string decode failed");
    }

    const std::string result = classify_adamova_v3_decimal(a_chars, b_chars);
    env->ReleaseStringUTFChars(a_raw, a_chars);
    env->ReleaseStringUTFChars(b_raw, b_chars);
    return env->NewStringUTF(result.c_str());
}

} // namespace

extern "C" const char* kraken_native_placeholder_status() {
    return "Kraken native C++ research core: Adamova Stage A diagnostics available for signed 128-bit and smooth arbitrary-size coefficients; no protocol, production crypto or networking logic.";
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_disser_kraken_nativecore_NativeCoreBridge_getNativeCoreStatus(
    JNIEnv* env,
    jobject /* this */
) {
    return env->NewStringUTF(kraken_native_placeholder_status());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_disser_kraken_nativecore_NativeCoreBridge_classifyAdamovaV3(
    JNIEnv* env,
    jobject /* this */,
    jlong a,
    jlong b
) {
    const std::string result = classify_adamova_v3_i128(static_cast<Int128>(a), static_cast<Int128>(b));
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_disser_kraken_nativecore_NativeCoreBridge_classifyAdamovaV3Decimal(
    JNIEnv* env,
    jobject /* this */,
    jstring a,
    jstring b
) {
    return classify_decimal_jni(env, a, b);
}

#ifdef KRAKEN_ADAMOVA_CLI
int main(int argc, char** argv) {
    if (argc == 2 && std::string(argv[1]) == "--status") {
        std::cout << kraken_native_placeholder_status() << '\n';
        return 0;
    }
    if (argc != 3) {
        std::cerr << "usage: adamova_native_cli <a> <b>\n";
        std::cerr << "       adamova_native_cli --status\n";
        return 2;
    }
    std::cout << classify_adamova_v3_decimal(argv[1], argv[2]) << '\n';
    return 0;
}
#endif
