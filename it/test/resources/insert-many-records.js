// db = connect("mongodb://localhost/jan-24-nic-change-calculator");

function randomBetween(min, max) {
    return (Math.random() * (max - min)) + min;
}

function randomCalculation(sessionId) {

    const annualSalary = randomBetween(5000, 300000);
    const year1EstimatedNic = randomBetween(100, 1000);
    const year2EstimatedNic = (year1EstimatedNic * 0.9);
    const roundedSaving = Math.floor((year1EstimatedNic - year2EstimatedNic));

    return {
        sessionId: sessionId,
        annualSalary: annualSalary,
        year1EstimatedNic: year1EstimatedNic,
        year2EstimatedNic: year2EstimatedNic,
        roundedSaving: roundedSaving,
        timestamp: new Date()
    };
}

function randomCalculationsForSessionId(sessionId, n) {

    const number = Math.min(Math.floor(randomBetween(1, 5)), n);
    const calculations = Array(number);

    for (let i = 0; i < number; i++) {
        calculations[i] = randomCalculation(sessionId);
    }

    return calculations;
}

function randomCalculations(number) {

    let calculations = [];

    while (calculations.length < number) {

        const sessionId = UUID();
        const n = number - calculations.length;

        calculations = calculations.concat(randomCalculationsForSessionId(sessionId, n));
    }

    return calculations;
}

const calculations = randomCalculations(10);

db.calculations.insertMany(calculations);