// Drives TypeSafe Jev's systemOne endpoint through OpenRouter, using the
// secretspec-managed OPENROUTER_API_KEY. Usage: node jevi.mjs < request.json
// Request body: {state, questions, model?}. Prints the answers JSON.
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

let key = "";
try {
	key = execSync("secretspec get OPENROUTER_API_KEY", { encoding: "utf8" }).trim();
} catch (err) {
	process.stderr.write(`secretspec could not provide OPENROUTER_API_KEY: ${err.message}\n`);
	process.exit(1);
}
if (!key) {
	process.stderr.write("OPENROUTER_API_KEY is empty\n");
	process.exit(1);
}

let request;
try {
	request = JSON.parse(readFileSync(0, "utf8"));
} catch (err) {
	process.stderr.write(`request is not valid JSON: ${err.message}\n`);
	process.exit(1);
}

const base = process.env.PI_JEV_BASE_URL?.replace(/\/+$/, "") ?? "https://openrouter.ai" + "/api/v1";
let res;
try {
	res = await fetch(`${base}/systemone`, {
		method: "POST",
		headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
		body: JSON.stringify({ model: "jev-latest", ...request }),
	});
} catch (err) {
	process.stderr.write(`systemone request failed: ${err.message}\n`);
	process.exit(1);
}
if (!res.ok) {
	process.stderr.write(`${res.status}: ${await res.text()}\n`);
	process.exit(1);
}
const json = await res.json();
process.stdout.write(JSON.stringify({ answers: json.answers, usage: json.usage }, null, 1) + "\n");
