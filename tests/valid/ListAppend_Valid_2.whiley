import whiley.lang.*

function append([int] input) -> [int]:
    [int] rs = []
    for i in 0 .. |input|:
        rs = rs ++ [input[i]]
    return rs

method main(System.Console sys) -> void:
    [int] xs = append("abcdefghijklmnopqrstuvwxyz")
    assert xs == [
            'a',
            'b',
            'c',
            'd',
            'e',
            'f',
            'g',
            'h',
            'i',
            'j',
            'k',
            'l',
            'm',
            'n',
            'o',
            'p',
            'q',
            'r',
            's',
            't',
            'u',
            'v',
            'w',
            'x',
            'y',
            'z'
            ]

