import re

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # if there is equals without hashCode
    if 'public boolean equals(' in content and 'public int hashCode(' not in content:
        # just append simple hashCode right before the last closing brace

        last_brace = content.rfind('}')
        if last_brace != -1:
            hash_code_str = """
    @Override
    public int hashCode() {
        int result = dxSearchCode != null ? dxSearchCode.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
"""
            new_content = content[:last_brace] + hash_code_str + content[last_brace:]
            with open(filepath, 'w') as f:
                f.write(new_content)

fix_file('src/main/java/io/github/carlos_emr/carlos/dxresearch/bean/dxResearchBean.java')
fix_file('src/main/java/io/github/carlos_emr/carlos/dxresearch/bean/dxCodeSearchBean.java')
