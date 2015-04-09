import whiley.lang.*

function f(int i) -> [int]:
    [int] arr1 = [1, 2, 64]
    [int] arr2 = arr1
    if i != |arr1|:
        arr2[2] = 3
    else:
        arr2[2] = i
    assert arr2[2] == |arr1|
    return arr1 ++ arr2

method main(System.Console sys) -> void:
    assert f(2) == [1, 2, 64, 1, 2, 3]
    assert f(3) == [1, 2, 64, 1, 2, 3]
