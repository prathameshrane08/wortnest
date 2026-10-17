import Foundation

/// A saved word's review state. New and forgotten cards go through short steps (1 min, 10 min);
/// after that each card has its own interval in days that grows by its ease factor every time you remember it.
/// The same rules live in index.html (web) and Srs.kt (Android).
struct Card: Codable {
    var due: Date = .distantPast
    var ivl: Int = 0            // days
    var ease: Double = 2.5
    var step: Int = 0           // learning step, -1 once the card is in review
    var reps: Int = 0
    var lapses: Int = 0
    var added: Date = Date()

    var isNew: Bool { reps == 0 }
    var isLearning: Bool { !isNew && step >= 0 }

    init() {}

    private enum CodingKeys: String, CodingKey { case due, ivl, ease, step, reps, lapses, added, box }

    // Cards saved by the old five-box version keep their progress.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        due = try c.decodeIfPresent(Date.self, forKey: .due) ?? .distantPast
        added = try c.decodeIfPresent(Date.self, forKey: .added) ?? .distantPast
        if let ease = try c.decodeIfPresent(Double.self, forKey: .ease) {
            self.ease = ease
            ivl = try c.decode(Int.self, forKey: .ivl)
            step = try c.decode(Int.self, forKey: .step)
            reps = try c.decode(Int.self, forKey: .reps)
            lapses = try c.decode(Int.self, forKey: .lapses)
        } else if let box = try c.decodeIfPresent(Int.self, forKey: .box), box > 1 {
            ivl = [0, 0, 1, 3, 7, 21][min(box, 5)]
            step = -1
            reps = box - 1
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(due, forKey: .due)
        try c.encode(ivl, forKey: .ivl)
        try c.encode(ease, forKey: .ease)
        try c.encode(step, forKey: .step)
        try c.encode(reps, forKey: .reps)
        try c.encode(lapses, forKey: .lapses)
        try c.encode(added, forKey: .added)
    }
}

enum Srs {
    static let steps: [Double] = [1, 10]     // minutes
    static let newPerDay = 20
    static let learnAhead: TimeInterval = 20 * 60
    static let gradeNames = ["Again", "Hard", "Good", "Easy"]

    /// grade: 0 again, 1 hard, 2 good, 3 easy
    static func next(_ c: Card, grade: Int, now: Date = Date()) -> Card {
        var n = c
        n.reps += 1
        let minute: TimeInterval = 60, day: TimeInterval = 86_400
        if c.step >= 0 {
            if grade == 0 {
                n.step = 0
                n.due = now + steps[0] * minute
            } else if grade == 1 {
                n.due = now + (c.step == 0 ? (steps[0] + steps[1]) / 2 : steps[c.step]) * minute
            } else if grade == 2 && c.step + 1 < steps.count {
                n.step = c.step + 1
                n.due = now + steps[n.step] * minute
            } else {
                n.step = -1
                n.ivl = grade == 3 ? max(4, c.ivl + 1) : max(1, c.ivl)
                n.due = now + Double(n.ivl) * day
            }
            return n
        }
        if grade == 0 {
            n.lapses += 1
            n.ease = max(1.3, c.ease - 0.2)
            n.ivl = max(1, Int((Double(c.ivl) * 0.5).rounded()))
            n.step = steps.count - 1
            n.due = now + steps[n.step] * minute
            return n
        }
        // Each button always gives a longer interval than the one to its left.
        let iv = Double(c.ivl)
        let hard = max(c.ivl + 1, Int((iv * 1.2).rounded()))
        let good = max(hard + 1, Int((iv * c.ease).rounded()))
        let easy = max(good + 1, Int((iv * c.ease * 1.3).rounded()))
        n.ivl = min([0, hard, good, easy][grade], 3650)
        if grade == 1 { n.ease = max(1.3, c.ease - 0.15) }
        if grade == 3 { n.ease = c.ease + 0.15 }
        n.due = now + Double(n.ivl) * day
        return n
    }

    static func label(_ c: Card, grade: Int, now: Date = Date()) -> String {
        let m = next(c, grade: grade, now: now).due.timeIntervalSince(now) / 60
        if m < 60 { return "\(max(1, Int(m.rounded()))) min" }
        if m < 1440 { return "\(Int((m / 60).rounded())) h" }
        let d = Int((m / 1440).rounded())
        if d < 31 { return d == 1 ? "1 day" : "\(d) days" }
        if d < 365 { return "\(Int((Double(d) / 30).rounded())) mo" }
        let y = (Double(d) / 365 * 10).rounded() / 10
        return y == y.rounded() ? "\(Int(y)) yr" : "\(y) yr"
    }
}
