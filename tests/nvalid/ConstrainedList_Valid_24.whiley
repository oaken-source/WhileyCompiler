import println from whiley.lang.System

type nat is int where $ >= 0

type Digraph is [{nat}] where no { v in $, w in v | w >= |$| }

function addEdge(Digraph g, nat from, nat to) => Digraph:
    mx = Math.max(from, to)
    while |g| <= mx:
        g = g + [{}]
    assert from < |g|
    g[from] = g[from] + {to}
    return g

method main(System.Console sys) => void:
    g = []
    g = addEdge(g, 1, 2)
    g = addEdge(g, 2, 3)
    g = addEdge(g, 3, 1)
    sys.out.println(g)