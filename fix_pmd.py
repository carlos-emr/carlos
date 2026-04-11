with open('.github/workflows/pmd.yml', 'r') as f:
    content = f.read()

import re

new_content = re.sub(r'          if \[ \! -d "\$\{PMD_HOME\}" \]; then.*?          fi',
                     """          if [ ! -d "${PMD_HOME}" ]; then
            echo "Downloading PMD ${PMD_VERSION}..."
            PMD_ZIP="pmd-dist-${PMD_VERSION}-bin.zip"
            PMD_BASE_URL="https://github.com/pmd/pmd/releases/download/pmd_releases%2F${PMD_VERSION}"
            wget -q "${PMD_BASE_URL}/${PMD_ZIP}"
            unzip -q "${PMD_ZIP}"
            rm -f "${PMD_ZIP}"
          else
            echo "PMD ${PMD_VERSION} already cached"
          fi""", content, flags=re.DOTALL)

with open('.github/workflows/pmd.yml', 'w') as f:
    f.write(new_content)
