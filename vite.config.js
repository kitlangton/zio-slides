import { resolve } from 'path'
import { minifyHtml, injectHtml } from 'vite-plugin-html'

const scalaVersion = '2.13'
// const scalaVersion = '3.0.0-RC1'

// https://vitejs.dev/config/
export default ({ mode }) => {
  const mainJS = `frontend/target/scala-${scalaVersion}/formula-${mode === 'production' ? 'opt' : 'fastopt'}/main.js`
  console.log('mainJS', mainJS)
  return {
    publicDir: './frontend/src/main/static/public',
//    plugins: [
//      ...(process.env.NODE_ENV === 'production' ? [
//        minifyHtml(),
//      ] : []),
//      injectHtml({
//        injectData: {
//          mainJS
//        }
//      })
//    ],
    resolve: {
      alias: {
        'stylesheets': resolve(__dirname, './frontend/src/main/static/stylesheets'),
      }
    }
  }
}
