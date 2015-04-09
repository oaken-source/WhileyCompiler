import whiley.lang.*

type liststr is [int] | [int]

function index(liststr l, int index) -> any
    requires index >= 0 && index < |l|:
    //
    return l[index]

method main(System.Console sys) -> void:
    [int] l = [1, 2, 3]
    assert index(l, 0) == 1
    assert index(l, 1) == 2
    assert index(l, 2) == 3
    [int] s = "Hello World"
    assert index(s, 0) == 'H'
    assert index(s, 1) == 'e'    
    assert index(s, 2) == 'l'
