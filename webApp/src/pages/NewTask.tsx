import {useState} from "react";
import ErrorPopup from "../components/ErrorPopup.tsx";
import '../App.css';

function NewTask() {
    const [error, setError] = useState<string | null>(null)
    return (
        <>
            <ErrorPopup error={error} onClose={() => setError(null)} />
            <form onSubmit={(e) => {
                e.preventDefault();
                const form = e.target;
                const formData = new FormData(form);
                const entries = Object.fromEntries(formData.entries());

                fetch("/api/task", {
                    method: "POST",
                    body: JSON.stringify(entries),
                }).then(r => {
                    if (r.ok) {
                        window.location.href = "/dashboard"
                    } else {
                        r.text().then(text => {
                            setError(`Failed to create task: ${text}`)
                        })
                    }
                }).catch(e => {
                    console.error(e)
                    setError("Failed to connect to server")
                });
            }}>
                <div className="column">
                    <h1 className="fullWidth">New Task</h1>
                    <button type="submit" className="button addButton">
                        Save
                    </button>
                </div>
                <div className="column">
                    <div className="form">
                        <input name="title" type="text" placeholder="Title" className="input"/>
                        <textarea name="description" placeholder="Description" className="input"/>
                    </div>
                </div>
            </form>
        </>
    )
}

export default NewTask;