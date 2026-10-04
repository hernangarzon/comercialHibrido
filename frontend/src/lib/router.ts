import { useEffect, useState } from 'react'

// Enrutado mínimo por hash (#/bandeja?c=123): sin dependencias y compatible con
// cualquier servidor estático, porque el backend solo sirve index.html en "/".

export type Page = 'bandeja' | 'resultados' | 'bot' | 'ajustes' | 'plataforma'

export interface Route {
  page: Page
  params: URLSearchParams
}

const PAGES: Page[] = ['bandeja', 'resultados', 'bot', 'ajustes', 'plataforma']

function parse(): Route {
  const [path = '', query = ''] = window.location.hash.replace(/^#\/?/, '').split('?')
  const page = PAGES.includes(path as Page) ? (path as Page) : 'bandeja'
  return { page, params: new URLSearchParams(query) }
}

export function navigate(page: Page, params?: Record<string, string>) {
  const query = params ? `?${new URLSearchParams(params)}` : ''
  window.location.hash = `/${page}${query}`
}

export function useRoute(): Route {
  const [route, setRoute] = useState(parse)
  useEffect(() => {
    const onChange = () => setRoute(parse())
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}
