import whiley.lang.*

type plistv6 is [int]

function f(plistv6 xs) -> int:
    return |xs|

function g(plistv6 left, plistv6 right) -> int:
    return f(left ++ right)

method main(System.Console sys) -> void:
    int r = g([1, 2, 3], [6, 7, 8])
    assert r == 6

