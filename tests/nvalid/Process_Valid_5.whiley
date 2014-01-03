import println from whiley.lang.System

type MyProc is ref {bool flag}

method run(MyProc this, System.Console sys) => void:
    if this->flag:
        sys.out.println("TRUE")
    else:
        sys.out.println("FALSE")

method main(System.Console sys) => void:
    mproc = new {flag: false}
    mproc.run(sys)