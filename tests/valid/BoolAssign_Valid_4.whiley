import whiley.lang.*

function f(int x, int y) -> int:
    bool a = x == y
    if a:
        return 1
    else:
        return x + y

function g(int x, int y) -> int:
    bool a = x >= y
    if !a:
        return x + y
    else:
        return 1

method main(System.Console sys) -> void:
    assert f(1, 1) == 1
    assert f(0, 0) == 1
    assert f(4, 345) == 349
    assert g(1, 1) == 1
    assert g(0, 0) == 1
    assert g(4, 345) == 349
