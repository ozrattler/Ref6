import { createContext, useContext, useState, useEffect } from 'react'
import { pb } from './pb'

const AuthContext = createContext(null)

function isAdminAuth(model) {
  return model != null && !model.collectionName
}

export function AuthProvider({ children }) {
  const [isValid, setIsValid] = useState(pb.authStore.isValid)
  const [isAdmin, setIsAdmin] = useState(isAdminAuth(pb.authStore.model))

  useEffect(() => {
    return pb.authStore.onChange(() => {
      setIsValid(pb.authStore.isValid)
      setIsAdmin(isAdminAuth(pb.authStore.model))
    })
  }, [])

  async function login(email, password) {
    try {
      await pb.admins.authWithPassword(email, password)
      return
    } catch {}
    await pb.collection('users').authWithPassword(email, password)
  }

  function logout() {
    pb.authStore.clear()
  }

  return (
    <AuthContext.Provider value={{ isValid, isAdmin, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
