const {test} = require('node:test');
const assert = require('node:assert/strict');
const {classify} = require('./change-scope.cjs');
const pom = version => `<artifactId>WHIMC-QRF-Agent</artifactId>\n<version>${version}</version>\n<dependency><version>1.0</version></dependency>`;

test('website assets and documentation skip heavy checks and releases', () => {
  assert.deepEqual(classify(['README.md', 'docs/map.svg', 'docs/map.png', 'docs/build-map.cjs', 'CONTRIBUTING.md']), {code:false,release:false});
});
test('source and packaged content release even when mixed with docs', () => {
  for (const file of ['src/main/java/Agent.java','src/main/resources/config.yml','src/main/resources/context.md','lib/plugin.jar','.mvn/wrapper/maven-wrapper.properties']) {
    assert.deepEqual(classify(['docs/map.png', file]), {code:true,release:true});
  }
});
test('workflow and classifier changes run checks without releasing', () => {
  assert.deepEqual(classify(['.github/workflows/ci.yml', '.github/scripts/change-scope.cjs']), {code:true,release:false});
});
test('project version-only reconciliation never releases', () => {
  assert.deepEqual(classify(['pom.xml'], pom('3.3.25'), pom('3.3.35')), {code:true,release:false});
});
test('dependency version changes and missing POMs do release', () => {
  assert.equal(classify(['pom.xml'], pom('3.3.35'), pom('3.3.35').replace('1.0','1.1')).release, true);
  assert.equal(classify(['pom.xml']).release, true);
});
test('empty diff does not run code checks or release', () => {
  assert.deepEqual(classify([]), {code:false,release:false});
});
