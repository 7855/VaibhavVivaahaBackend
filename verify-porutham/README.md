# verify-porutham

Rule-by-rule check of the Tamil 10-porutham engine (`utils/PoruthamEngine.java` + `utils/StarInfo.java`)
against the source lists recorded in CLAUDE.md (2026-09-27). Run after any change to either file:

    ./mvnw -q -o compile
    cd verify-porutham && javac -cp ../target/classes PoruthamCheck.java && java -cp ../target/classes:. PoruthamCheck

Prints PASS/FAIL per rule and exits non-zero on any failure. Add a line whenever an astrologer
disputes a result — a wrong table produces a plausible number, so nothing else catches it.
