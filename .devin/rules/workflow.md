---
trigger: always_on
description: "Enforces a strict plan-before-code and compile workflow for all features."
---

# Feature Implementation Workflow

You must follow this exact sequence whenever the user asks you to implement a feature, refactor code, or fix a bug:

1. **Review Design Mechanics:** Before proposing any changes, look at the project's `README.md` to identify and adhere to key design mechanics.
2. **Do Not Implement Immediately:** You are strictly forbidden from writing code or modifying files upon the first prompt of a feature request.
3. **Propose a Plan First:** Provide a concise list of exactly 3 to 4 points outlining how you intend to implement the feature and how it will behaves. 
4. **Clarify Ambiguities:** End your proposal with 1-2 targeted questions to clarify any ambiguities or edge cases. Halt and wait for user confirmation.
5. **Execution & Verification:** Once the user explicitly confirms the plan or corrects mistakes, implement the feature. Immediately after writing the code, run the compilation command (`./gradlew compileJava`) via the terminal to ensure it builds successfully.
