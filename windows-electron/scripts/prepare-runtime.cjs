const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const root = path.resolve(__dirname, '..')
const output = path.join(root, 'build', 'runtime')
const javaHome = process.env.ETOILEBRIDGE_JAVA_HOME || process.env.JAVA_HOME

if (!javaHome) {
  throw new Error('ETOILEBRIDGE_JAVA_HOME or JAVA_HOME is required to build the bundled runtime.')
}

const jlinkName = process.platform === 'win32' ? 'jlink.exe' : 'jlink'
const jlink = path.join(javaHome, 'bin', jlinkName)

if (!fs.existsSync(jlink)) {
  throw new Error(`jlink not found: ${jlink}`)
}

fs.rmSync(output, { recursive: true, force: true })
fs.mkdirSync(path.dirname(output), { recursive: true })

execFileSync(
  jlink,
  [
    '--add-modules',
    [
      'java.base',
      'java.desktop',
      'java.logging',
      'java.xml',
      'jdk.charsets'
    ].join(','),
    '--strip-debug',
    '--no-header-files',
    '--no-man-pages',
    '--compress=zip-6',
    '--output',
    output
  ],
  { stdio: 'inherit' }
)

const javaName = process.platform === 'win32' ? 'java.exe' : 'java'
const javaPath = path.join(output, 'bin', javaName)

if (!fs.existsSync(javaPath)) {
  throw new Error(`Bundled runtime was created but java is missing: ${javaPath}`)
}

console.log(`Prepared bundled runtime: ${output}`)
