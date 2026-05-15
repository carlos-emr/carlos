## 2026-05-15 - Fixing Missing and Invalid Javadocs
**Learning:** When fixing missing or invalid Javadocs, especially removing `@author` tags per project guidelines, ensure that any added class-level Javadoc is meaningful and descriptive, not just repeating the class name. It is also important to insert the Javadoc block in the correct syntactic location—immediately preceding the class declaration and above any class-level annotations (like `@Entity` or `@Repository`)—to avoid breaking Javadoc generation tools.

**Action:** Used a script to accurately locate class definitions (including jumping over annotations) to properly inject new, descriptive Javadoc blocks.
