const fs = require('node:fs')
const path = require('node:path')
const pngToIcoModule = require('png-to-ico')
const pngToIco = pngToIcoModule.default || pngToIcoModule

async function main() {
  const root = path.resolve(__dirname, '..')
  const source = path.join(root, 'src', 'renderer', 'src', 'assets', 'icon_windows.png')
  const outputDir = path.join(root, 'build')
  const output = path.join(outputDir, 'icon_windows.ico')

  if (!fs.existsSync(source)) {
    throw new Error(`Windows icon source not found: ${source}`)
  }

  fs.mkdirSync(outputDir, { recursive: true })
  const ico = await pngToIco(source)
  fs.writeFileSync(output, ico)
  console.log(`Prepared Windows icon: ${output}`)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
