const fs = require('node:fs');
const {execFileSync} = require('node:child_process');

function documentation(file) {
  return file.startsWith('docs/') || /^[^/]+\.md$/i.test(file)
    || /^LICENSE(?:\.[^/]+)?$/i.test(file);
}

// Ignore only this project's version, never dependency/plugin versions.
function withoutProjectVersion(pom) {
  return pom.replace(/(<artifactId>WHIMC-QRF-Agent<\/artifactId>\s*<version>)[^<]+(<\/version>)/, '$1PROJECT_VERSION$2');
}

function classify(files, oldPom, newPom) {
  return {
    code: files.some(file => !documentation(file)),
    release: files.some(file => {
      if (file === 'pom.xml') {
        return oldPom === undefined || newPom === undefined || withoutProjectVersion(oldPom) !== withoutProjectVersion(newPom);
      }
      return /^(?:src\/|lib\/|\.mvn\/)/.test(file) || ['mvnw', 'mvnw.cmd'].includes(file);
    })
  };
}

function git(...args) { return execFileSync('git', args, {encoding:'utf8'}); }

if (require.main === module) {
  const event = JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'));
  const pr = event.pull_request;
  const base = pr ? pr.base.sha : event.before;
  const head = pr ? pr.head.sha : event.after;
  const initial = !base || /^0+$/.test(base);
  const files = (initial ? git('ls-tree','-r','--name-only','-z', head) :
    git('diff','--name-only','-z',...(pr ? [`${base}...${head}`] : [base, head])))
    .split('\0').filter(Boolean);
  let oldPom, newPom;
  if (files.includes('pom.xml') && !initial) {
    // Missing/deleted POMs fail closed as build-input changes.
    try { oldPom = git('show', `${base}:pom.xml`); } catch {}
    try { newPom = git('show', `${head}:pom.xml`); } catch {}
  }
  const scope = classify(files, oldPom, newPom);
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `code=${scope.code}\nrelease=${scope.release}\n`);
  console.log(`${files.length} changed files; code checks=${scope.code}; release=${scope.release}`);
}

module.exports = {classify, withoutProjectVersion};
