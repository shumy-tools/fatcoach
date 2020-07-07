import { RouteConfig } from 'vue-router'

const routes: RouteConfig[] = [
  {
    path: '/', redirect: '/model/query',
    component: () => import('layouts/MainLayout.vue'),
    children: [
      { path: 'model', component: () => import('layouts/ModelLayout.vue'),
        children: [
          { path: 'query', component: () => import('pages/QueryPage.vue') },
          { path: 'entity/:id', component: () => import('pages/EntityPage.vue') },
        ]
      }
    ]
  },

  { path: '*', component: () => import('pages/Error404.vue') }
]

export default routes
