import { useState, type FormEvent } from 'react'
import { api } from '../api'

interface NewModuleFormProps {
  onCreated: () => void | Promise<void>
}

export default function NewModuleForm({
  onCreated,
}: NewModuleFormProps) {
  const [open, setOpen] = useState<boolean>(false)
  const [name, setName] = useState<string>('')
  const [technicalDocPath, setTechnicalDocPath] = useState<string>('')
  const [businessDocPath, setBusinessDocPath] = useState<string>('')
  const [submitting, setSubmitting] = useState<boolean>(false)

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()

    const trimmedName: string = name.trim()

    if (!trimmedName) {
      return
    }

    setSubmitting(true)

    try {
      await api.createModule(
        trimmedName,
        technicalDocPath.trim() || null,
        businessDocPath.trim() || null,
      )

      setName('')
      setTechnicalDocPath('')
      setBusinessDocPath('')
      setOpen(false)

      await onCreated()
    } finally {
      setSubmitting(false)
    }
  }

  if (!open) {
    return (
      <button
        className="btn btn-primary"
        type="button"
        onClick={() => setOpen(true)}
      >
        + Track module
      </button>
    )
  }

  return (
    <form
      className="new-module-form"
      onSubmit={submit}
    >
      <input
        type="text"
        placeholder="Module name (e.g. PaymentService)"
        value={name}
        onChange={(event) => setName(event.target.value)}
        autoFocus
        required
      />

      <input
        type="text"
        placeholder="Technical doc path (optional)"
        value={technicalDocPath}
        onChange={(event) => setTechnicalDocPath(event.target.value)}
      />

      <input
        type="text"
        placeholder="Business doc path (optional)"
        value={businessDocPath}
        onChange={(event) => setBusinessDocPath(event.target.value)}
      />

      <button
        className="btn btn-primary"
        type="submit"
        disabled={submitting}
      >
        {submitting ? 'Adding…' : 'Add'}
      </button>

      <button
        className="btn btn-ghost"
        type="button"
        onClick={() => setOpen(false)}
        disabled={submitting}
      >
        Cancel
      </button>
    </form>
  )
}
