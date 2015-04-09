import whiley.lang.*

function f(int x, int y) -> (int r)
requires (x >= 0) && (y >= 0)
ensures r > 0:
    //
    bool a = true
    if x < y:
        a = false
    if !a:
        return x + y
    else:
        return 123

method main(System.Console sys) -> void:
    assert f(1,1) == 123
    assert f(2,1) == 123
    assert f(1,2) == 3
