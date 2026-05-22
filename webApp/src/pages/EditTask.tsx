import {useSearchParams} from "react-router-dom";
import {useEffect, useState} from "react";
import type {Task} from "../Objects.tsx";
import ErrorPopup from "../components/ErrorPopup.tsx";
import ConfirmPopup from "../components/ConfirmPopup.tsx";
import '../App.css';
import './EditTask.css';

function EditTask() {
    const [searchParams] = useSearchParams();
    const id = searchParams.get("id") || "";

    const [task, setTask] = useState<Task | null>(null);
    const [fail, setFail] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [showDelete, setShowDelete] = useState(false);

    useEffect(() => {
        fetch(`/api/tasks/${id}`)
            .then(r => {
                if (r.status == 401)
                    window.location.href = `/login?redirect=${encodeURIComponent(window.location.href)}`
                return r.json()
            })
            .then(r => setTask(r))
            .catch(e => {
                setFail(true);
                console.error(e)
            })
    }, [id]);

    if (fail) {
        return (
            <>
                <div className="column">
                    <h1 className="fullWidth">Task Not Found</h1>
                    <button className="button addButton" onClick={() => {
                        window.location.href = `/task?id=${id}`
                    }}>
                        Back
                    </button>
                </div>
            </>
        );
    }

    return task !== null && (
        <>
            <ErrorPopup error={error} onClose={() => setError(null)} />
            <form onSubmit={(e) => {
                e.preventDefault();
                const form = e.target;
                const formData = new FormData(form);
                const entries = {
                    title: formData.get("title"),
                    description: formData.get("description"),
                    completed: formData.get("completed") == "on"
                };

                fetch(`/api/tasks/${id}`, {
                    method: "PATCH",
                    body: JSON.stringify(entries),
                }).then(r => {
                    if (r.ok) {
                        window.location.href = `/task?id=${id}`
                    } else {
                        r.text().then(text => {
                            setError(`Failed to update task: ${text}`)
                        })
                    }
                }).catch(e => {
                    console.error(e)
                    setError("Failed to connect to server")
                });
            }}>
                <div className="column">
                    <h1 className="fullWidth">Edit Task</h1>
                    <button type="submit" className="button addButton">
                        Save
                    </button>
                </div>
                <div className="column">
                    <div className="form">
                        <input name="title" type="text" defaultValue={task.title} className="input"/>
                        <textarea name="description" defaultValue={task.description} className="input"/>
                        <div className="column editCompleted">
                            <span>Completed</span>
                            <input name="completed" type="checkbox" defaultChecked={task.completed} className="checkbox"/>
                            <div className="fullWidth" />
                            <button type="button" className="button deleteButton" onClick={() => setShowDelete(true)}>
                                Delete task
                            </button>
                            <ConfirmPopup show={showDelete} onClose={() => setShowDelete(false)}>
                                <span>Delete this task?</span>
                                <div className="confirm-actions">
                                    <button type="button" className="button" onClick={() => setShowDelete(false)}>Cancel</button>
                                    <button type="button" className="button deleteButton" onClick={() => {
                                        setShowDelete(false)
                                        fetch(`/api/tasks/${id}`, { method: "DELETE" })
                                            .then(r => {
                                                if (r.ok)
                                                    window.location.href = "/dashboard"
                                                else
                                                    r.text().then(t => setError(`Failed to delete: ${t}`))
                                            })
                                            .catch(() => setError("Failed to connect to server"))
                                    }}>Delete</button>
                                </div>
                            </ConfirmPopup>
                        </div>
                    </div>
                </div>
            </form>
        </>
    )
}

export default EditTask;