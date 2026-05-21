import {useEffect, useState} from "react";
import type {Task} from "../Task.tsx";
import ErrorPopup from "../components/ErrorPopup.tsx";
import PlusIcon from "../components/PlusIcon.tsx";
import '../App.css';
import './Dashboard.css';

function Dashboard() {
    const [tasks, setTasks] = useState<null | Task[]>(null)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        fetch("/api/tasks")
            .then(r => {
                if (r.status == 401)
                    window.location.href = `/login?redirect=${encodeURIComponent(window.location.href)}`
                return r.json()
            })
            .then(r => setTasks(r))
            .catch(e => console.error(e))
    }, []);

    return (
        <>
            <ErrorPopup error={error} onClose={() => setError(null)} />
            <div className="column">
                <h1 className="fullWidth">Your Tasks</h1>
                <button
                    type="button"
                    className="button addButton"
                    onClick={() => {
                        window.location.href = "/newTask"
                    }}>
                    <PlusIcon />
                    Add
                </button>
            </div>
            <div className="column">
                <table className="tasks">
                    <thead>
                    <tr>
                        <th scope="col" className="completedColumn"></th>
                        <th scope="col">title</th>
                        <th scope="col">description</th>
                        <th scope="col" className="dateColumn">added</th>
                        <th scope="col" className="openColumn"></th>
                    </tr>
                    </thead>
                    <tbody>
                    {tasks !== null && (
                        tasks.length == 0 ? (
                            <tr>
                                <td colSpan={5} className="textCenter">
                                    No tasks yet. Try adding one!
                                </td>
                            </tr>
                        ) : tasks.map((task) => (
                            <tr key={task.id}>
                                <td>
                                    <input
                                        type="checkbox"
                                        className="checkbox"
                                        checked={task.completed}
                                        onChange={() => {
                                            const newValue = !task.completed
                                            setTasks(prev => prev!.map(t =>
                                                t.id === task.id ? { ...t, completed: newValue } : t
                                            ))
                                            fetch(`/api/tasks/${task.id}`, {
                                                method: "PATCH",
                                                body: JSON.stringify({completed: newValue})
                                            }).then(r => {
                                                if (!r.ok)
                                                    r.text().then(x => setError(`Failed to update task state: ${x}`))
                                            }).catch(e => {
                                                console.error(e);
                                                setError("Failed to connect to server")
                                            })
                                        }} />
                                </td>
                                <td className="textLeft">{task.title}</td>
                                <td>
                                    <div className="descCell textLeft">{task.description}</div>
                                </td>
                                <td>{task.added}</td>
                                <td className="openCell">
                                    <button className="button" onClick={() => {
                                        window.location.href = "/task?id=" + task.id;
                                    }}>
                                        Open
                                    </button>
                                </td>
                            </tr>
                        ))
                    )}
                    </tbody>
                </table>
            </div>
        </>
    )
}

export default Dashboard;