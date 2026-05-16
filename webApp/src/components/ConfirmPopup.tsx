import { useState, useEffect, type ReactNode } from 'react'
import './ConfirmPopup.css'

function ConfirmPopup({ show, onClose, children }: { show: boolean; onClose: () => void; children: ReactNode }) {
    const [mounted, setMounted] = useState(false)
    const [closing, setClosing] = useState(false)

    useEffect(() => {
        if (show) {
            setMounted(true)
            setClosing(false)
        } else if (mounted) {
            setClosing(true)
            setTimeout(() => {
                setMounted(false)
                setClosing(false)
            }, 200)
        }
    }, [mounted, show])

    if (!mounted) return null

    return (
        <div className={'confirm-overlay' + (closing ? ' fade-out' : '')} onClick={onClose}>
            <div className={'confirm-dialog' + (closing ? ' fade-out' : '')} onClick={e => e.stopPropagation()}>
                {children}
            </div>
        </div>
    )
}

export default ConfirmPopup
