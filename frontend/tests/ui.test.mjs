import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const styles = readFileSync(new URL('../src/style.css', import.meta.url), 'utf8')

test('operations center exposes an accessible status-first layout', () => {
  assert.match(app, /class="skip-link"/)
  assert.match(app, /class="command-strip"/)
  assert.match(app, /实时通道/)
  assert.match(app, /严重告警/)
  assert.match(app, /服务事故中心/)
  assert.match(app, /接手处理/)
  assert.match(styles, /incident-grid/)
  assert.match(styles, /--cyan: #0ea5a8/)
  assert.match(styles, /prefers-reduced-motion/)
})

test('responsive rules keep the command center usable on small screens', () => {
  assert.match(styles, /@media \(max-width: 760px\)/)
  assert.match(styles, /@media \(max-width: 480px\)/)
  assert.match(styles, /grid-template-columns: 1fr/)
})
