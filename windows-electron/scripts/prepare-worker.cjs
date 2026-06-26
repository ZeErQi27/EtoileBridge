const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const libDir = path.join(root, 'converter-worker', 'build', 'install', 'converter-worker', 'lib')

if (!fs.existsSync(libDir)) {
  throw new Error(`converter-worker lib directory is missing: ${libDir}. Run npm run worker:build first.`)
}

const jars = fs.readdirSync(libDir).filter((name) => name.endsWith('.jar'))

if (jars.length === 0) {
  throw new Error(`converter-worker lib directory contains no jars: ${libDir}`)
}

console.log(`Prepared converter-worker libs: ${libDir}`)
console.log(jars.map((name) => `- ${name}`).join('\n'))
