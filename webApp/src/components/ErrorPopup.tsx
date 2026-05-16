import { useState, useEffect } from 'react'
import './ErrorPopup.css'

function ErrorPopup({ error, onClose }: { error: string | null; onClose: () => void }) {
    const [closing, setClosing] = useState(false)

    useEffect(() => {
        if (error) setClosing(false)
    }, [error])

    if (!error) return null

    const handleClose = () => {
        setClosing(true)
        setTimeout(() => onClose(), 200)
    }

    return (
        <div className={'error-popup' + (closing ? ' fade-out' : ' fade-in')}>
            <span>{error}</span>
            <button className="error-close" onClick={handleClose}>×</button>
        </div>
    )
}

export default ErrorPopup
