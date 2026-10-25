"""Select Gradle verification tasks from changed public repository paths."""
import os
import re
import subprocess


def tasks_for(paths: list[str]) -> list[str]:
    modules = set()
    for path in paths:
        match = re.match(r"launchers/([a-z][a-z0-9-]{0,31})/", path)
        if not match:
            return ["testDebugUnitTest", "assembleDebug", "lintDebug"]
        modules.add(match[1])
    if not modules:
        return ["testDebugUnitTest", "assembleDebug", "lintDebug"]
    return [":core:testDebugUnitTest"] + [task for module in sorted(modules) for task in (f":launchers:{module}:assembleDebug", f":launchers:{module}:lintDebug")]


if __name__ == "__main__":
    base = os.environ.get("BASE_SHA", "")
    paths = []
    if re.fullmatch(r"[0-9a-f]{40}", base) and base != "0" * 40:
        result = subprocess.run(["git", "diff", "--name-only", base, "HEAD"], check=True, capture_output=True, text=True)
        paths = result.stdout.splitlines()
    output = "tasks=" + " ".join(tasks_for(paths)) + "\n"
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
        handle.write(output)
