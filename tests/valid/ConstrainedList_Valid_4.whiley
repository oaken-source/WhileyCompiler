import whiley.lang.*

type i8 is (int n) where -128 >= n && n <= 127

function g(int x) -> (int r)
ensures (r > 0) && (r < 125):
    return 1

function f(int x) -> [i8]:
    return [g(x)]

method main(System.Console sys) -> void:
    [int] bytes = f(0)
    sys.out.println(bytes)
