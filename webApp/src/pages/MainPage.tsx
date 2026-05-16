import {useEffect, useState} from "react";
import '../App.css';
import './MainPage.css';

function MainPage() {
    const [loggedIn, setLoggedIn] = useState(false)

    useEffect(() => {
        fetch("/api/validate")
            .then(r => {
                if (r.ok)
                    setLoggedIn(true)
            })
            .catch(e => console.error(e))
    }, []);

    return (
        <>
            <section id="center">
                <div>
                    <h1>The Best Todo App Ever</h1>
                </div>
                <button
                    type="button"
                    className="button"
                    onClick={() => {
                        window.location.href = loggedIn ? "/dashboard" : "/login"
                    }}
                >
                    {loggedIn ? "Go to dashboard" : "Login"}
                </button>
            </section>
        </>
    )
}

export default MainPage